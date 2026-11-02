package kz.kayak.bookkeeper.repository;
import jakarta.persistence.LockModeType;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ChargeRepository extends JpaRepository<Charge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select charge from Charge charge where charge.id=:id")
    Optional<Charge> findWithLockById(Long id);
    List<Charge> findByPerson(Person person);
    List<Charge> findBySubscription(Subscription subscription);
    @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.charge=:charge")
    long allocatedFor(Charge charge);
}
