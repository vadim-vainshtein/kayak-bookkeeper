package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.TrainingSession;
import kz.kayak.bookkeeper.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    @Query("select session.subscription.id from TrainingSession session where session.id = :id")
    Optional<Long> findSubscriptionIdById(@Param("id") Long id);
    @Query("select session from TrainingSession session join fetch session.subscription where session.subscription in :subscriptions")
    List<TrainingSession> findBySubscriptionInWithSubscription(@Param("subscriptions") Collection<Subscription> subscriptions);
    @Modifying(flushAutomatically = true) @Query("delete from TrainingSession session where session.subscription = :subscription")
    void deleteBySubscription(@Param("subscription") Subscription subscription);
}
