package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
@Entity @Table(name = "subscriptions")
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private int capacity;
    private String title;
    @Column(length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SubscriptionStatus status = SubscriptionStatus.DRAFT;
    private Long poolRentalAmount;
    protected Subscription() { }
    public Subscription(Integer capacity) { this(capacity, null, null); }
    public Subscription(Integer capacity, String title, String description) { this.capacity=capacity == null ? 8 : capacity; this.title=title; this.description=description; }
    public Long getId() { return id; } public int getCapacity() { return capacity; } public String getTitle() { return title; } public String getDescription() { return description; } public SubscriptionStatus getStatus() { return status; } public Long getPoolRentalAmount() { return poolRentalAmount; }
    public void updateMetadata(String title, String description) { this.title = title; this.description = description; }
    public void updateCapacity(int capacity) { this.capacity = capacity; }
    public void updatePoolRentalAmount(long amount) { this.poolRentalAmount = amount; }
    public void finalizeWith(long amount) { this.poolRentalAmount=amount; this.status=SubscriptionStatus.FINALIZED; }
    public void complete() { this.status = SubscriptionStatus.COMPLETED; }
}
