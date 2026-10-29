package kz.kayak.bookkeeper.repository;
import kz.kayak.bookkeeper.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> { }
