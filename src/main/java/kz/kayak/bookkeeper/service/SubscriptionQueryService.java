package kz.kayak.bookkeeper.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kz.kayak.bookkeeper.api.dto.SubscriptionView;
import kz.kayak.bookkeeper.api.dto.SessionView;
import kz.kayak.bookkeeper.domain.Subscription;
import kz.kayak.bookkeeper.domain.SubscriptionMember;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.repository.SubscriptionMemberRepository;
import kz.kayak.bookkeeper.repository.SubscriptionRepository;
import kz.kayak.bookkeeper.repository.TrainingSessionRepository;
import kz.kayak.bookkeeper.repository.SessionAttendanceRepository;
import org.springframework.stereotype.Service;

import static kz.kayak.bookkeeper.service.EntityLookup.get;

/** Builds complete subscription cards, including batched list reads. */
@Service
public class SubscriptionQueryService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionMemberRepository members;
    private final TrainingSessionRepository sessions;
    private final SubscriptionAccess access;
    private final SessionAttendanceRepository attendance;
    private final AttendanceService attendanceService;

    public SubscriptionQueryService(
            SubscriptionRepository subscriptions,
            SubscriptionMemberRepository members,
            TrainingSessionRepository sessions,
            SubscriptionAccess access, SessionAttendanceRepository attendance, AttendanceService attendanceService) {
        this.subscriptions = subscriptions;
        this.members = members;
        this.sessions = sessions;
        this.access = access;
        this.attendance = attendance;
        this.attendanceService = attendanceService;
    }

    public List<SubscriptionView> listSubscriptions() {
        List<Subscription> all = subscriptions.findAll().stream().sorted(Comparator.comparing(Subscription::getId)).toList();
        if (all.isEmpty()) return List.of();
        Map<Long, List<SubscriptionMember>> membersBySubscription = members.findBySubscriptionInWithSubscriptionAndPerson(all).stream().collect(Collectors.groupingBy(member -> member.getSubscription().getId()));
        List<TrainingSession> allSessions = sessions.findBySubscriptionInWithSubscription(all);
        Map<Long, List<TrainingSession>> sessionsBySubscription = allSessions.stream().collect(Collectors.groupingBy(session -> session.getSubscription().getId()));
        Map<Long, Long> counts = attendanceCounts(allSessions);
        return all.stream().map(subscription -> subscriptionView(subscription, membersBySubscription.getOrDefault(subscription.getId(), List.of()), sessionsBySubscription.getOrDefault(subscription.getId(), List.of()), counts)).toList();
    }

    public SubscriptionView getSubscription(long id) {
        return subscriptionView(get(subscriptions, id, "Subscription"));
    }

    public SubscriptionView subscriptionView(Subscription s) {
        return subscriptionView(s, members.findBySubscription(s), access.sessionsFor(s));
    }

    public SubscriptionView subscriptionView(Subscription s, List<SubscriptionMember> subscriptionMembers, List<TrainingSession> subscriptionSessions) {
        return subscriptionView(s, subscriptionMembers, subscriptionSessions, attendanceCounts(subscriptionSessions));
    }

    private Map<Long, Long> attendanceCounts(List<TrainingSession> subscriptionSessions) {
        if (subscriptionSessions.isEmpty()) return Map.of();
        return attendance.countsBySessions(subscriptionSessions).stream().collect(Collectors.toMap(
                SessionAttendanceRepository.SessionCount::getSessionId, SessionAttendanceRepository.SessionCount::getAttendedCount));
    }

    private SubscriptionView subscriptionView(Subscription s, List<SubscriptionMember> subscriptionMembers,
            List<TrainingSession> subscriptionSessions, Map<Long, Long> counts) {
        return new SubscriptionView(s.getId(), s.getCapacity(), s.getTitle(), s.getDescription(), s.getStatus(), s.getPoolRentalAmount(), subscriptionMembers.stream().sorted(Comparator.comparing((SubscriptionMember member) -> member.getPerson().getFullName(), String.CASE_INSENSITIVE_ORDER).thenComparing(SubscriptionMember::getId)).map(EntityViews::memberView).toList(), subscriptionSessions.stream().sorted(Comparator.comparing(TrainingSession::getStartsAt)).map(session -> new SessionView(session.getId(), session.getStartsAt(), counts.getOrDefault(session.getId(), 0L), session.getAttendanceRevision() > 0, attendanceService.available(session))).toList());
    }
}
