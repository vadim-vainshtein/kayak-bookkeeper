package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "training_sessions")
public class TrainingSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Subscription subscription;
    @Column(nullable = false) private LocalDateTime startsAt;
    protected TrainingSession() { }
    public TrainingSession(Subscription subscription, LocalDateTime startsAt) { this.subscription=subscription; this.startsAt=startsAt; }
    public Long getId() { return id; } public Subscription getSubscription() { return subscription; } public LocalDateTime getStartsAt() { return startsAt; }
}
