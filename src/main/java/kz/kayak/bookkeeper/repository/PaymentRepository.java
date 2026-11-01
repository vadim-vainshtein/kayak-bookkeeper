package kz.kayak.bookkeeper.repository;
import jakarta.persistence.LockModeType;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.*;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id=:id")
    java.util.Optional<Payment> findWithLockById(Long id);
    java.util.List<Payment> findByPerson(Person person);
    @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.payment=:payment")
    long allocatedFor(Payment payment);
}
