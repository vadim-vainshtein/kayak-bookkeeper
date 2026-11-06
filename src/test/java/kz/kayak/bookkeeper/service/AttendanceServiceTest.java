package kz.kayak.bookkeeper.service;

import java.time.*;
import java.util.List;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(AttendanceServiceTest.FixedClock.class)
@Transactional
public class AttendanceServiceTest {
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    @TestConfiguration
    public static class FixedClock {
        @Bean @Primary Clock attendanceTestClock() {
            return Clock.fixed(Instant.parse("2026-11-07T07:00:00Z"), ZoneId.of("Asia/Qyzylorda"));
        }
    }

    @Autowired AttendanceService attendance;
    @Autowired PersonService people;
    @Autowired PersonRepository personRepository;
    @Autowired PricingRateService rates;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired SubscriptionQueryService queries;
    @Autowired FinalizedSubscriptionEditService edits;
    @Autowired BalanceService balances;
    @Autowired PaymentService payments;
    @Autowired ChargeService charges;

    @Test
    void defaultsToAbsentAndSavesUnlimitedGuestsAtTodaysRateWithoutDuplicatingCharges() {
        var member = person("Участник");
        var first = person("Гость по тарифу");
        var second = person("Гость со скидкой");
        var subscription = finalized(member, TODAY.minusDays(2).atTime(19, 0));
        long sessionId = subscription.sessions().getFirst().id();
        rates.createRate(new RateRequest(RateCode.DROP_IN, 150, TODAY.minusDays(10), TODAY.minusDays(1)));
        rates.createRate(new RateRequest(RateCode.DROP_IN, 250, TODAY, null));
        var empty = attendance.getAttendance(sessionId);
        assertThat(empty.revision()).isZero();
        assertThat(empty.people()).singleElement().satisfies(row -> assertThat(row.present()).isFalse());
        assertThat(empty.dropInAmount()).isEqualTo(250);
        assertThat(subscription.sessions().getFirst().attendedCount()).isZero();

        var saved = attendance.saveAttendance(sessionId, new AttendanceUpdateRequest(0L, List.of(member.id()), List.of(
                guest(first, null), guest(second, 175L), new AttendanceGuestRequest(null, "Новый гость", "700", 300L))));
        assertThat(saved.people()).hasSize(4).allSatisfy(row -> assertThat(row.present()).isTrue());
        assertThat(balances.balance(first.id()).charges()).singleElement().satisfies(charge -> {
            assertThat(charge.amount()).isEqualTo(250);
            assertThat(charge.subscriptionId()).isEqualTo(subscription.id());
            assertThat(charge.chargedAt()).isEqualTo(TODAY.minusDays(2));
        });
        assertThat(balances.balance(member.id()).charges()).singleElement().satisfies(charge -> assertThat(charge.amount()).isEqualTo(1000));
        assertThat(personRepository.findAll()).filteredOn(person -> person.getFullName().equals("Новый гость")).singleElement();
        assertThat(queries.getSubscription(subscription.id()).sessions().getFirst().attendedCount()).isEqualTo(4);

        List<Long> chargeIds = saved.people().stream().filter(AttendancePersonView::guest).map(AttendancePersonView::chargeId).sorted().toList();
        var repeated = attendance.saveAttendance(sessionId, new AttendanceUpdateRequest(saved.revision(), List.of(member.id()),
                saved.people().stream().filter(AttendancePersonView::guest).map(row -> new AttendanceGuestRequest(row.personId(), null, null, row.amount())).toList()));
        assertThat(repeated.people().stream().filter(AttendancePersonView::guest).map(AttendancePersonView::chargeId).sorted().toList()).isEqualTo(chargeIds);
        assertThatThrownBy(() -> attendance.saveAttendance(sessionId, new AttendanceUpdateRequest(0L, List.of(), List.of())))
                .isInstanceOf(DomainException.class).hasMessageContaining("уже изменены");
    }

    @Test
    void allowsExplicitPriceWithoutTariffAndFreeVisitsAndRemovesUnpaidGuestCharges() {
        var member = person("Участник");
        var guest = person("Гость");
        long id = finalized(member, TODAY.atTime(12, 0)).sessions().getFirst().id();
        assertThat(attendance.getAttendance(id).dropInAmount()).isNull();
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(), List.of(guest(guest, null)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("Укажите сумму");
        var saved = attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(), List.of(guest(guest, 125L))));
        saved = attendance.saveAttendance(id, new AttendanceUpdateRequest(saved.revision(), List.of(), List.of(guest(guest, 0L))));
        assertThat(saved.people()).filteredOn(AttendancePersonView::guest).singleElement().satisfies(row -> {
            assertThat(row.amount()).isZero();
            assertThat(row.chargeId()).isNull();
        });
        assertThat(balances.balance(guest.id()).charges()).isEmpty();
        rates.createRate(new RateRequest(RateCode.DROP_IN, 0, TODAY, null));
        saved = attendance.saveAttendance(id, new AttendanceUpdateRequest(saved.revision(), List.of(), List.of(guest(guest, null))));
        assertThat(saved.people()).filteredOn(AttendancePersonView::guest).singleElement().satisfies(row -> assertThat(row.amount()).isZero());
        attendance.saveAttendance(id, new AttendanceUpdateRequest(saved.revision(), List.of(), List.of()));
        assertThat(attendance.getAttendance(id).people()).singleElement().satisfies(row -> assertThat(row.present()).isFalse());
    }

    @Test
    void rejectsDraftsFutureSessionsDuplicatesAndMembersChargedAsGuestsButAllowsCompleted() {
        var member = person("Участник");
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(TODAY.atTime(10, 0)), 1));
        assertThatThrownBy(() -> attendance.saveAttendance(draft.sessions().getFirst().id(), new AttendanceUpdateRequest(0L, List.of(), List.of())))
                .isInstanceOf(DomainException.class).hasMessageContaining("после начала");
        var future = finalized(member, TODAY.atTime(12, 1));
        assertThat(future.sessions().getFirst().attendanceAvailable()).isFalse();
        assertThatThrownBy(() -> attendance.saveAttendance(future.sessions().getFirst().id(), new AttendanceUpdateRequest(0L, List.of(), List.of())))
                .isInstanceOf(DomainException.class);
        var past = finalized(member, TODAY.atTime(12, 0));
        long id = past.sessions().getFirst().id();
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(member.id(), member.id()), List.of())))
                .isInstanceOf(DomainException.class).hasMessageContaining("дважды");
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(), List.of(guest(member, 100L)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("основном списке");
        subscriptions.completeSubscription(past.id());
        assertThat(attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(member.id()), List.of())).people())
                .singleElement().satisfies(row -> assertThat(row.present()).isTrue());
    }

    @Test
    void protectsAllocatedChargesAndPreventsIndependentLedgerChanges() {
        var member = person("Участник");
        var guest = person("Оплативший гость");
        var subscription = finalized(member, TODAY.minusDays(1).atTime(19, 0));
        long id = subscription.sessions().getFirst().id();
        var saved = attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(), List.of(guest(guest, 250L))));
        long chargeId = saved.people().stream().filter(AttendancePersonView::guest).findFirst().orElseThrow().chargeId();
        var payment = payments.createPayment(new PaymentRequest(guest.id(), 200, TODAY, PaymentMethod.CASH, null));
        payments.allocatePayment(payment.id(), new AllocationRequest(chargeId, 200));
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(1L, List.of(), List.of(guest(guest, 199L)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("меньше распределённой");
        assertThatThrownBy(() -> attendance.saveAttendance(id, new AttendanceUpdateRequest(1L, List.of(), List.of())))
                .isInstanceOf(DomainException.class).hasMessageContaining("Нельзя убрать");
        assertThatThrownBy(() -> charges.editCharge(chargeId, new ChargeEditRequest(300, TODAY, "Изменить")))
                .isInstanceOf(DomainException.class).hasMessageContaining("окне посещений");
        assertThatThrownBy(() -> charges.deleteCharge(chargeId)).isInstanceOf(DomainException.class).hasMessageContaining("окне посещений");
        var updated = attendance.saveAttendance(id, new AttendanceUpdateRequest(1L, List.of(), List.of(guest(guest, 200L))));
        assertThat(updated.people()).filteredOn(AttendancePersonView::guest).singleElement().satisfies(row -> {
            assertThat(row.allocated()).isEqualTo(200);
            assertThat(row.amount()).isEqualTo(200);
        });
    }

    @Test
    void groupsAttendanceCountsAcrossSessionsAndSubscriptionsWithZeroDefaults() {
        var member = person("Состав");
        var guest = person("Гость");
        var first = finalized(member, TODAY.minusDays(2).atTime(19, 0));
        var expanded = edits.updateFinalizedSubscription(first.id(), new FinalizedSubscriptionUpdateRequest(
                List.of(TODAY.minusDays(2).atTime(19, 0), TODAY.minusDays(1).atTime(19, 0)),
                List.of(new MemberRequest(member.id(), false, false, false)), 1, "Две тренировки", null, false));
        var other = finalized(member, TODAY.atTime(10, 0));
        attendance.saveAttendance(expanded.sessions().getFirst().id(), new AttendanceUpdateRequest(0L,
                List.of(member.id()), List.of(guest(guest, 100L))));
        attendance.saveAttendance(expanded.sessions().getLast().id(), new AttendanceUpdateRequest(0L,
                List.of(member.id()), List.of()));
        var all = queries.listSubscriptions();
        assertThat(all.stream().filter(subscription -> subscription.id().equals(first.id())).findFirst().orElseThrow().sessions())
                .extracting(SessionView::attendedCount).containsExactly(2L, 1L);
        assertThat(all.stream().filter(subscription -> subscription.id().equals(other.id())).findFirst().orElseThrow().sessions())
                .extracting(SessionView::attendedCount).containsExactly(0L);
        assertThat(queries.getSubscription(first.id()).sessions()).extracting(SessionView::attendedCount).containsExactly(2L, 1L);
    }

    @Test
    void preservesSessionAndHistoryWhenEditingSubscriptionAndRejectsRemovalOfRecordedSession() {
        var member = person("Первый состав");
        var next = person("Новый состав");
        var guest = person("Гость");
        var subscription = finalized(member, TODAY.minusDays(1).atTime(19, 0));
        long id = subscription.sessions().getFirst().id();
        attendance.saveAttendance(id, new AttendanceUpdateRequest(0L, List.of(member.id()), List.of(guest(guest, 250L))));
        var updated = edits.updateFinalizedSubscription(subscription.id(), new FinalizedSubscriptionUpdateRequest(
                List.of(subscription.sessions().getFirst().startsAt(), TODAY.plusDays(6).atTime(19, 0)),
                List.of(new MemberRequest(next.id(), false, false, false)), 1, "Новое", null, false));
        assertThat(updated.sessions().getFirst().id()).isEqualTo(id);
        assertThat(attendance.getAttendance(id).people()).hasSize(3);
        assertThat(balances.balance(guest.id()).charges()).singleElement().satisfies(charge -> assertThat(charge.amount()).isEqualTo(250));
        assertThatThrownBy(() -> edits.updateFinalizedSubscription(subscription.id(), new FinalizedSubscriptionUpdateRequest(
                List.of(TODAY.plusDays(6).atTime(19, 0)), List.of(new MemberRequest(next.id(), false, false, false)), 1, "Новое", null, false)))
                .isInstanceOf(DomainException.class).hasMessageContaining("с сохранёнными посещениями");
    }

    private PersonView person(String name) { return people.createPerson(new PersonRequest(name, null, null)); }
    private AttendanceGuestRequest guest(PersonView person, Long amount) { return new AttendanceGuestRequest(person.id(), null, null, amount); }
    private SubscriptionView finalized(PersonView member, LocalDateTime startsAt) {
        if (rates.listRates().stream().noneMatch(rate -> rate.code() == RateCode.POOL_RENTAL))
            rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 1000, TODAY.minusDays(30), null));
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(startsAt), 1));
        subscriptions.addMember(draft.id(), new MemberRequest(member.id(), false, false, false));
        return finalization.finalizeSubscription(draft.id());
    }
}
