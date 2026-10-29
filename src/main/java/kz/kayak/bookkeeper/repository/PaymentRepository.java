package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.*;
import org.springframework.data.jpa.repository.*;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    java.util.List<Payment> findByPerson(Person person);
    @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.payment=:payment")
    long allocatedFor(Payment payment);
}
