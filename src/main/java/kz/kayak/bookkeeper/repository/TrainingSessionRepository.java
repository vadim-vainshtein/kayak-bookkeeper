package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    @Query("select session from TrainingSession session join fetch session.subscription where session.subscription in :subscriptions")
    List<TrainingSession> findBySubscriptionInWithSubscription(@Param("subscriptions") Collection<Subscription> subscriptions);
}
