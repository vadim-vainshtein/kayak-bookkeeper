package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "payment_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "charge_id"}))
public class PaymentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Payment payment;
    @ManyToOne(optional = false) private Charge charge;
    @Column(nullable = false) private long amount;
    @Column(name = "allocated_at", nullable = false) private LocalDate allocatedAt;
    protected PaymentAllocation() { }
    public PaymentAllocation(Payment payment, Charge charge, long amount) { this(payment, charge, amount, LocalDate.now()); }
    public PaymentAllocation(Payment payment, Charge charge, long amount, LocalDate allocatedAt) { this.payment=payment; this.charge=charge; this.amount=amount; this.allocatedAt=allocatedAt; }
    public Long getId() { return id; } public Payment getPayment() { return payment; } public Charge getCharge() { return charge; } public long getAmount() { return amount; } public LocalDate getAllocatedAt() { return allocatedAt; }
}
