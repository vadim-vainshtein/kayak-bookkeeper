package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Person person;
    @Column(nullable = false) private long amount;
    @Column(nullable = false) private LocalDate paidAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod method;
    @Column(length = 1000) private String note;
    protected Payment() { }
    public Payment(Person person, long amount, LocalDate paidAt, PaymentMethod method, String note) { this.person=person; this.amount=amount; this.paidAt=paidAt; this.method=method; this.note=note; }
    public Long getId() { return id; } public Person getPerson() { return person; } public long getAmount() { return amount; } public LocalDate getPaidAt() { return paidAt; } public PaymentMethod getMethod() { return method; } public String getNote() { return note; }
    public void setAmount(long amount) { this.amount = amount; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }
    public void setNote(String note) { this.note = note; }
}
