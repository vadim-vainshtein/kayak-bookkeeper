package kz.kayak.bookkeeper.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.RateCode;
import kz.kayak.bookkeeper.repository.SessionAttendanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Separate transactions deliberately interleave a roster save between the report's reads. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attendance-report-snapshot;MODE=PostgreSQL")
@Import(AttendanceServiceTest.FixedClock.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceReportSnapshotTest {
    @Autowired AttendanceReportService reports;
    @Autowired AttendanceService attendance;
    @Autowired PersonService people;
    @Autowired PricingRateService rates;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired EntityManager entityManager;
    @MockitoSpyBean SessionAttendanceRepository entries;

    @Test
    void concurrentAttendanceSaveCannotMixNewVisitsWithOldSessionRevision() throws Exception {
        var date = LocalDate.of(2026, 11, 6);
        rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 1000, date, null));
        var member = people.createPerson(new PersonRequest("Concurrent report member", null, null));
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(date.atTime(19, 0)), 1));
        subscriptions.addMember(draft.id(), new MemberRequest(member.id(), false, false, false));
        var subscription = finalization.finalizeSubscription(draft.id());
        long sessionId = subscription.sessions().getFirst().id();
        var sessionsRead = new CountDownLatch(1);
        var resumeEntries = new CountDownLatch(1);
        // Spring Data query methods are abstract; delegate through an unspied
        // repository using the same thread-bound EntityManager/transaction.
        var realEntries = new JpaRepositoryFactory(entityManager).getRepository(SessionAttendanceRepository.class);
        doAnswer(invocation -> {
            sessionsRead.countDown();
            if (!resumeEntries.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for roster save");
            return realEntries.reportEntries(invocation.getArgument(0), invocation.getArgument(1));
        }).when(entries).reportEntries(anyCollection(), any(LocalDateTime.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reading = executor.submit(() -> reports.attendanceReport(List.of(subscription.id()), null));
            try {
                assertThat(sessionsRead.await(10, TimeUnit.SECONDS)).isTrue();
                attendance.saveAttendance(sessionId, new AttendanceUpdateRequest(0L, List.of(member.id()), List.of()));
            } finally { resumeEntries.countDown(); }
            var snapshot = reading.get(10, TimeUnit.SECONDS);
            assertThat(snapshot.summary().recordedSessions()).isZero();
            assertThat(snapshot.summary().totalVisits()).isZero();
            assertThat(snapshot.subscriptions().getFirst().people()).singleElement()
                    .satisfies(row -> assertThat(row.visits()).isEmpty());
        }
        var refreshed = reports.attendanceReport(List.of(subscription.id()), null);
        assertThat(refreshed.summary().recordedSessions()).isEqualTo(1);
        assertThat(refreshed.summary().totalVisits()).isEqualTo(1);
    }
}
