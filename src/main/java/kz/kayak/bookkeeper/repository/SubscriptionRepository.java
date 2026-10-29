package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.Subscription;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findWithLockById(Long id);
}
