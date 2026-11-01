package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.Payment;
import kz.kayak.bookkeeper.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByPayment(Payment payment);
}
