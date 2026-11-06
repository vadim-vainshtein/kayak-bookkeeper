package kz.kayak.bookkeeper.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.*;
import kz.kayak.bookkeeper.domain.*;
import kz.kayak.bookkeeper.repository.*;
import kz.kayak.bookkeeper.repository.SessionAttendanceRepository.ReportEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Builds a read-only attendance snapshot with a fixed number of batched reads. */
@Service
public class AttendanceReportService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMemberRepository members;
    private final TrainingSessionRepository sessions;
    private final SessionAttendanceRepository attendance;
    private final Clock clock;

    public AttendanceReportService(SubscriptionRepository subscriptions, SubscriptionMemberRepository members,
            TrainingSessionRepository sessions, SessionAttendanceRepository attendance, Clock clock) {
        this.subscriptions = subscriptions;
        this.members = members;
        this.sessions = sessions;
        this.attendance = attendance;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AttendanceReportView attendanceReport(List<Long> subscriptionIds, AttendanceReportStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Subscription> selected = selectedSubscriptions(subscriptionIds).stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.DRAFT)
                .filter(subscription -> status == null || subscription.getStatus().name().equals(status.name()))
                .sorted(Comparator.comparing(Subscription::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Subscription::getId)).toList();
        if (selected.isEmpty()) return new AttendanceReportView(now, clock.getZone().getId(),
                new AttendanceReportSummaryView(0, 0, 0, 0, 0, 0, 0), List.of());

        Map<Long, List<SubscriptionMember>> membersBySubscription = members.findBySubscriptionInWithSubscriptionAndPerson(selected)
                .stream().collect(Collectors.groupingBy(member -> member.getSubscription().getId()));
        Map<Long, List<TrainingSession>> sessionsBySubscription = sessions.findBySubscriptionInWithSubscription(selected)
                .stream().collect(Collectors.groupingBy(session -> session.getSubscription().getId()));
        List<ReportEntry> entries = attendance.reportEntries(selected, now);
        Map<Long, List<ReportEntry>> entriesBySubscription = entries.stream().collect(Collectors.groupingBy(ReportEntry::getSubscriptionId));
        List<SubscriptionAttendanceReportView> rows = selected.stream().map(subscription -> subscriptionReport(subscription,
                membersBySubscription.getOrDefault(subscription.getId(), List.of()),
                sessionsBySubscription.getOrDefault(subscription.getId(), List.of()),
                entriesBySubscription.getOrDefault(subscription.getId(), List.of()), now)).toList();
        long memberVisits = entries.stream().filter(entry -> !entry.getGuest()).count();
        long guestVisits = entries.size() - memberVisits;
        return new AttendanceReportView(now, clock.getZone().getId(), new AttendanceReportSummaryView(
                rows.stream().mapToLong(row -> row.summary().scheduledSessions()).sum(),
                rows.stream().mapToLong(row -> row.summary().startedSessions()).sum(),
                rows.stream().mapToLong(row -> row.summary().recordedSessions()).sum(),
                memberVisits, guestVisits, entries.size(), entries.stream().map(ReportEntry::getPersonId).distinct().count()), rows);
    }

    private List<Subscription> selectedSubscriptions(List<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) return subscriptions.findAll();
        Set<Long> ids = new LinkedHashSet<>(subscriptionIds);
        if (ids.contains(null)) throw new NoSuchElementException("Subscription was not found");
        List<Subscription> selected = subscriptions.findAllById(ids);
        Set<Long> found = selected.stream().map(Subscription::getId).collect(Collectors.toSet());
        ids.stream().filter(id -> !found.contains(id)).findFirst().ifPresent(id -> {
            throw new NoSuchElementException("Subscription " + id + " was not found");
        });
        return selected;
    }

    private SubscriptionAttendanceReportView subscriptionReport(Subscription subscription,
            List<SubscriptionMember> currentMembers, List<TrainingSession> subscriptionSessions,
            List<ReportEntry> entries, LocalDateTime now) {
        Map<Long, List<ReportEntry>> entriesBySession = entries.stream().collect(Collectors.groupingBy(ReportEntry::getSessionId));
        List<AttendanceReportSessionView> sessionRows = subscriptionSessions.stream()
                .sorted(Comparator.comparing(TrainingSession::getStartsAt).thenComparing(TrainingSession::getId))
                .map(session -> {
                    boolean started = !session.getStartsAt().isAfter(now);
                    List<ReportEntry> visitors = entriesBySession.getOrDefault(session.getId(), List.of());
                    long guestVisits = visitors.stream().filter(ReportEntry::getGuest).count();
                    return new AttendanceReportSessionView(session.getId(), session.getStartsAt(), started,
                            started && session.getAttendanceRevision() > 0, visitors.size() - guestVisits, guestVisits, visitors.size());
                }).toList();
        Map<Long, PersonRow> people = new HashMap<>();
        for (SubscriptionMember member : currentMembers) {
            Person person = member.getPerson();
            people.put(person.getId(), new PersonRow(person.getId(), person.getFullName(), true));
        }
        for (ReportEntry entry : entries) {
            PersonRow person = people.computeIfAbsent(entry.getPersonId(), id -> new PersonRow(id, entry.getPersonName(), false));
            person.visits.add(new AttendanceReportVisitView(entry.getSessionId(), entry.getGuest()));
        }
        List<AttendanceReportPersonView> personRows = people.values().stream()
                .sorted(Comparator.comparing((PersonRow person) -> person.name, String.CASE_INSENSITIVE_ORDER).thenComparing(person -> person.id))
                .map(person -> {
                    long guestVisits = person.visits.stream().filter(AttendanceReportVisitView::guest).count();
                    return new AttendanceReportPersonView(person.id, person.name, person.currentMember,
                            person.visits.size() - guestVisits, guestVisits, person.visits.size(), List.copyOf(person.visits));
                }).toList();
        long memberVisits = entries.stream().filter(entry -> !entry.getGuest()).count();
        AttendanceReportSummaryView summary = new AttendanceReportSummaryView(sessionRows.size(),
                sessionRows.stream().filter(AttendanceReportSessionView::started).count(),
                sessionRows.stream().filter(AttendanceReportSessionView::recorded).count(),
                memberVisits, entries.size() - memberVisits, entries.size(), entries.stream().map(ReportEntry::getPersonId).distinct().count());
        return new SubscriptionAttendanceReportView(subscription.getId(), subscription.getTitle(), subscription.getStatus(),
                currentMembers.size(), summary, sessionRows, personRows);
    }

    private static final class PersonRow {
        final Long id;
        final String name;
        final boolean currentMember;
        final List<AttendanceReportVisitView> visits = new ArrayList<>();
        PersonRow(Long id, String name, boolean currentMember) { this.id = id; this.name = name; this.currentMember = currentMember; }
    }
}
