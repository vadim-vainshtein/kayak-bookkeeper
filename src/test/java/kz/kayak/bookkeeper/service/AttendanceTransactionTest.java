package kz.kayak.bookkeeper.service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.NoSuchElementException;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.PaymentMethod;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No surrounding test transaction: inspect the committed state after failed saves. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-transactions;MODE=PostgreSQL")
@Import(AttendanceServiceTest.FixedClock.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceTransactionTest {
    @Autowired AttendanceService attendance;
    @Autowired PersonService people;
    @Autowired PersonRepository personRepository;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired PricingRateService rates;
    @Autowired PaymentService payments;
    @Autowired BalanceService balances;
    @Autowired FinalizedSubscriptionEditService edits;
    @MockitoSpyBean SubscriptionAccess access;

    @Test
    void failedGuestRemovalRollsBackNewPeopleChargesAndMemberMarks() {
        var member = people.createPerson(new PersonRequest("Rollback member", null, null));
        var guest = people.createPerson(new PersonRequest("Paid guest", null, null));
        long id = finalized(member).sessions().getFirst().id();
        var saved = attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(member.id()),
                List.of(new AttendanceGuestRequest(guest.id(), null, null, 250L))));
        long chargeId = saved.people().stream().filter(AttendancePersonView::guest).findFirst().orElseThrow().chargeId();
        payments.createPaymentWithAllocation(new PaymentWithAllocationRequest(guest.id(), 100, LocalDate.of(2026, 11, 7), PaymentMethod.CASH, null, chargeId, 100L));
        long peopleBefore = personRepository.count();
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(1L, List.of(),
                List.of(new AttendanceGuestRequest(null, "Must roll back", null, 300L)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("Нельзя убрать");
        assertThat(personRepository.count()).isEqualTo(peopleBefore);
        assertThat(attendance.getAttendance(id).revision()).isEqualTo(1);
        assertThat(attendance.getAttendance(id).people()).hasSize(2).allSatisfy(row -> assertThat(row.present()).isTrue());
        assertThat(balances.balance(guest.id()).charges()).singleElement().satisfies(charge -> {
            assertThat(charge.id()).isEqualTo(chargeId);
            assertThat(charge.allocated()).isEqualTo(100);
        });
    }

    @Test
    void sessionRemovedBeforeAttendanceLockReturnsNotFoundWithoutPartialWrites() throws Exception {
        var member = people.createPerson(new PersonRequest("Removed session member", null, null));
        var subscription = finalized(member);
        long id = subscription.sessions().getFirst().id();
        var reachedLock = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("attendance-save-delete-race")) {
                reachedLock.countDown();
                if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for finalized edit");
            }
            return invocation.callRealMethod();
        }).when(access).lockedSubscription(subscription.id());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> {
                Thread.currentThread().setName("attendance-save-delete-race");
                try {
                    attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(),
                            List.of(new AttendanceGuestRequest(null, "Must not be created", null, 100L))));
                    return null;
                } catch (Exception failure) { return failure; }
            });
            try {
                assertThat(reachedLock.await(10, TimeUnit.SECONDS)).isTrue();
                edits.updateFinalizedSubscription(subscription.id(), new FinalizedSubscriptionUpdateRequest(
                        List.of(LocalDate.of(2026, 11, 8).atTime(19, 0)),
                        List.of(new MemberRequest(member.id(), false, false, false)), 1, "Перенос", null, false));
            } finally { resume.countDown(); }
            assertThat(result.get(10, TimeUnit.SECONDS)).isInstanceOf(NoSuchElementException.class);
        }
        assertThat(personRepository.findAll()).noneMatch(person -> person.getFullName().equals("Must not be created"));
    }

    @Test
    void concurrentSavesCannotDuplicateNewGuestOrCharge() throws Exception {
        var member = people.createPerson(new PersonRequest("Concurrent member", null, null));
        long id = finalized(member).sessions().getFirst().id();
        var start = new CountDownLatch(1);
        var request = new AttendanceUpdateRequest(0L, List.of(), List.of(new AttendanceGuestRequest(null, "Concurrent new guest", null, 100L)));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveAfterStart(start, id, request));
            var second = executor.submit(() -> saveAfterStart(start, id, request));
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        var rows = attendance.getAttendance(id).people().stream().filter(AttendancePersonView::guest).toList();
        assertThat(rows).hasSize(1);
        assertThat(personRepository.findAll()).filteredOn(person -> person.getFullName().equals("Concurrent new guest")).hasSize(1);
        assertThat(balances.balance(rows.getFirst().personId()).charges()).hasSize(1);
    }

    private boolean saveAfterStart(CountDownLatch start, long id, AttendanceUpdateRequest request) throws InterruptedException {
        start.await();
        try { attendance.saveAttendance(id, request); return true; }
        catch (DomainException expected) { return false; }
    }

    private SubscriptionView finalized(PersonView member) {
        if (rates.listRates().stream().noneMatch(rate -> rate.code() == RateCode.POOL_RENTAL))
            rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 1000, LocalDate.of(2026, 11, 1), null));
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(LocalDate.of(2026, 11, 6).atTime(19, 0)), 1));
        subscriptions.addMember(draft.id(), new MemberRequest(member.id(), false, false, false));
        return finalization.finalizeSubscription(draft.id());
    }
}
