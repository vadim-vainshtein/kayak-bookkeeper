package kz.kayak.bookkeeper.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.SessionAttendanceRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(AttendanceServiceTest.FixedClock.class)
@Transactional
class AttendanceReportServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    @Autowired AttendanceReportService reports;
    @Autowired AttendanceService attendance;
    @Autowired PersonService people;
    @Autowired PricingRateService rates;
    @Autowired SubscriptionService subscriptions;
    @Autowired SubscriptionFinalizationService finalization;
    @Autowired FinalizedSubscriptionEditService edits;
    @Autowired TrainingSessionRepository sessions;
    @Autowired SessionAttendanceRepository attendanceRepository;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void includesCurrentZeroVisitMembersAndHistoricalRolesButNeverFutureAttendance() {
        var fixture = history();
        var report = reports.attendanceReport(null, null);
        assertThat(report.generatedAt()).isEqualTo(TODAY.atTime(12, 0));
        assertThat(report.timeZone()).isEqualTo("Asia/Qyzylorda");
        assertThat(report.subscriptions()).hasSize(2);
        assertThat(report.summary()).isEqualTo(new AttendanceReportSummaryView(6, 5, 4, 3, 1, 4, 2));
        var first = report.subscriptions().stream().filter(row -> row.subscriptionId().equals(fixture.first().id())).findFirst().orElseThrow();
        assertThat(first.summary()).isEqualTo(new AttendanceReportSummaryView(5, 4, 3, 2, 1, 3, 2));
        assertThat(first.memberCount()).isEqualTo(2);
        assertThat(first.sessions()).extracting(AttendanceReportSessionView::totalVisits).containsExactly(2L, 0L, 0L, 1L, 0L);
        assertThat(first.sessions().get(1).recorded()).isTrue(); // Saved empty session.
        assertThat(first.sessions().get(2).recorded()).isFalse(); // Started, never saved.
        assertThat(first.sessions().get(3).started()).isTrue(); // Exactly at the club clock's boundary.
        assertThat(first.sessions().get(4).started()).isFalse();
        assertThat(first.sessions().get(4).recorded()).isFalse(); // Ignore malformed future revision too.
        assertThat(first.people()).hasSize(3).noneMatch(person -> person.personId().equals(fixture.futureOnly().id()));
        assertThat(first.people().stream().filter(row -> row.personId().equals(fixture.formerMember().id())).findFirst().orElseThrow())
                .satisfies(row -> { assertThat(row.currentMember()).isFalse(); assertThat(row.memberVisits()).isEqualTo(1); });
        assertThat(first.people().stream().filter(row -> row.personId().equals(fixture.currentZero().id())).findFirst().orElseThrow())
                .satisfies(row -> { assertThat(row.currentMember()).isTrue(); assertThat(row.totalVisits()).isZero(); assertThat(row.visits()).isEmpty(); });
        var changedRole = first.people().stream().filter(row -> row.personId().equals(fixture.changedRole().id())).findFirst().orElseThrow();
        assertThat(changedRole.currentMember()).isTrue();
        assertThat(changedRole.memberVisits()).isEqualTo(1);
        assertThat(changedRole.guestVisits()).isEqualTo(1);
        assertThat(changedRole.visits()).extracting(AttendanceReportVisitView::guest).containsExactly(true, false);
    }

    @Test
    void filtersBeforeSummaryDeduplicatesIdsAndDoesNotFallBackFromEmptyIntersection() {
        var fixture = history();
        var completed = reports.attendanceReport(List.of(fixture.first().id(), fixture.completed().id(), fixture.completed().id()), AttendanceReportStatus.COMPLETED);
        assertThat(completed.subscriptions()).singleElement().satisfies(row -> assertThat(row.subscriptionId()).isEqualTo(fixture.completed().id()));
        assertThat(completed.summary()).isEqualTo(new AttendanceReportSummaryView(1, 1, 1, 1, 0, 1, 1));
        assertThat(reports.attendanceReport(List.of(fixture.first().id()), AttendanceReportStatus.COMPLETED).subscriptions()).isEmpty();
        var draftOnly = reports.attendanceReport(List.of(fixture.draft().id()), null);
        assertThat(draftOnly.subscriptions()).isEmpty();
        assertThat(draftOnly.summary()).isEqualTo(new AttendanceReportSummaryView(0, 0, 0, 0, 0, 0, 0));
        assertThatThrownBy(() -> reports.attendanceReport(List.of(fixture.first().id(), Long.MAX_VALUE), AttendanceReportStatus.COMPLETED))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void defaultAbsenceDoesNotInventVisitsOrExcludeUnrecordedSessions() {
        var member = person("Ни разу не отмечен");
        var subscription = finalized("Без отметок", List.of(TODAY.minusDays(1).atTime(19, 0), TODAY.plusDays(1).atTime(19, 0)), member);
        var report = reports.attendanceReport(List.of(subscription.id()), null);
        assertThat(report.summary()).isEqualTo(new AttendanceReportSummaryView(2, 1, 0, 0, 0, 0, 0));
        assertThat(report.subscriptions().getFirst().people()).singleElement().satisfies(row -> assertThat(row.totalVisits()).isZero());
    }

    @Test
    void batchesLargeMatricesWithoutAQueryPerCellSessionOrCharge() {
        history();
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            var report = reports.attendanceReport(null, null);
            assertThat(report.summary().totalVisits()).isEqualTo(4);
            // Subscriptions, current members, sessions, and sparse attendance rows.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        } finally { statistics.setStatisticsEnabled(enabled); }
    }

    private Fixture history() {
        var former = person("Бывший участник");
        var zero = person("Участник без посещений");
        var changed = person("Гость, ставший участником");
        var futureOnly = person("Только ошибочная будущая отметка");
        List<LocalDateTime> dates = List.of(TODAY.minusDays(2).atTime(19, 0), TODAY.minusDays(1).atTime(19, 0),
                TODAY.atTime(11, 0), TODAY.atTime(12, 0), TODAY.atTime(12, 0, 1));
        var first = finalized("Первый", dates, former, zero);
        attendance.saveAttendance(first.sessions().get(0).id(), new AttendanceUpdateRequest(0L, List.of(former.id()),
                List.of(new AttendanceGuestRequest(changed.id(), null, null, 100L))));
        attendance.saveAttendance(first.sessions().get(1).id(), new AttendanceUpdateRequest(0L, List.of(), List.of()));
        edits.updateFinalizedSubscription(first.id(), new FinalizedSubscriptionUpdateRequest(dates,
                List.of(new MemberRequest(zero.id(), false, false, false), new MemberRequest(changed.id(), false, false, false)),
                2, "Первый", null, false));
        attendance.saveAttendance(first.sessions().get(3).id(), new AttendanceUpdateRequest(0L, List.of(changed.id()), List.of()));
        // Data outside the public service's rules must not leak future visitors into a report.
        var future = sessions.findById(first.sessions().get(4).id()).orElseThrow();
        future.recordAttendance();
        attendanceRepository.save(new SessionAttendance(future, entityManager.getReference(Person.class, futureOnly.id()), true));

        var second = finalized("Завершённый", List.of(TODAY.minusDays(1).atTime(18, 0)), former);
        attendance.saveAttendance(second.sessions().getFirst().id(), new AttendanceUpdateRequest(0L, List.of(former.id()), List.of()));
        second = subscriptions.completeSubscription(second.id());
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(List.of(TODAY.minusDays(3).atTime(19, 0)), 1, "Черновик", null));
        return new Fixture(first, second, draft, former, zero, changed, futureOnly);
    }

    private PersonView person(String name) { return people.createPerson(new PersonRequest(name, null, null)); }
    private SubscriptionView finalized(String title, List<LocalDateTime> dates, PersonView... currentMembers) {
        if (rates.listRates().stream().noneMatch(rate -> rate.code() == RateCode.POOL_RENTAL))
            rates.createRate(new RateRequest(RateCode.POOL_RENTAL, 1000, TODAY.minusDays(30), null));
        var draft = subscriptions.createExplicitSubscription(new ExplicitSubscriptionRequest(dates, currentMembers.length, title, null));
        for (var member : currentMembers) subscriptions.addMember(draft.id(), new MemberRequest(member.id(), false, false, false));
        return finalization.finalizeSubscription(draft.id());
    }
    private record Fixture(SubscriptionView first, SubscriptionView completed, SubscriptionView draft,
            PersonView formerMember, PersonView currentZero, PersonView changedRole, PersonView futureOnly) {}
}
