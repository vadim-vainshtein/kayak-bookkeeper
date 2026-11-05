package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "charges")
public class Charge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Person person;
    @ManyToOne private Subscription subscription;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ChargeType type;
    @Column(nullable = false) private long amount;
    @Column(nullable = false) private LocalDate chargedAt;
    @Column(length = 1000) private String description;
    protected Charge() { }
    public Charge(Person person, Subscription subscription, ChargeType type, long amount, LocalDate chargedAt) { this(person, subscription, type, amount, chargedAt, null); }
    public Charge(Person person, Subscription subscription, ChargeType type, long amount, LocalDate chargedAt, String description) { this.person=person; this.subscription=subscription;  this.type=type; this.amount=amount; this.chargedAt=chargedAt; this.description=description; }
    public Long getId() { return id; } public Person getPerson() { return person; } public Subscription getSubscription() { return subscription; } public ChargeType getType() { return type; } public long getAmount() { return amount; } public LocalDate getChargedAt() { return chargedAt; } public String getDescription() { return description; }
    public void edit(long amount, LocalDate chargedAt, String description) { edit(amount, chargedAt, description, subscription); }
    public void edit(long amount, LocalDate chargedAt, String description, Subscription subscription) { this.amount=amount; this.chargedAt=chargedAt; this.description=description; this.subscription=subscription; }
}
