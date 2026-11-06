package kz.kayak.bookkeeper.repository;

import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;
import kz.kayak.bookkeeper.domain.SessionAttendance;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, Long> {
    List<SessionAttendance> findBySession(TrainingSession session);
    interface SessionCount {
        Long getSessionId();
        long getAttendedCount();
    }
    @Query("select entry.session.id as sessionId, count(entry) as attendedCount from SessionAttendance entry where entry.session in :sessions group by entry.session.id")
    List<SessionCount> countsBySessions(@Param("sessions") Collection<TrainingSession> sessions);
    interface ReportEntry {
        Long getPersonId();
        String getPersonName();
        Long getSessionId();
        Long getSubscriptionId();
        boolean getGuest();
    }
    // Scalar projection deliberately avoids loading the attendance charge graph.
    @Query("select entry.person.id as personId, entry.person.fullName as personName, entry.session.id as sessionId, entry.session.subscription.id as subscriptionId, entry.guest as guest from SessionAttendance entry where entry.session.subscription in :subscriptions and entry.session.startsAt <= :now order by entry.session.startsAt, entry.session.id, entry.person.id")
    List<ReportEntry> reportEntries(@Param("subscriptions") Collection<Subscription> subscriptions, @Param("now") LocalDateTime now);
    boolean existsByChargeId(Long chargeId);
}
