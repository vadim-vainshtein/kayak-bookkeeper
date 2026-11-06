package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "training_sessions")
public class TrainingSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Subscription subscription;
    @Column(nullable = false) private LocalDateTime startsAt;
    private Long poolRentalAmount;
    @Column(nullable = false) private long attendanceRevision;
    protected TrainingSession() { }
    public TrainingSession(Subscription subscription, LocalDateTime startsAt) { this.subscription=subscription; this.startsAt=startsAt; }
    public Long getPoolRentalAmount() { return poolRentalAmount; }
    public long getAttendanceRevision() { return attendanceRevision; }
    public void recordAttendance() { attendanceRevision++; }
    public void snapshotPoolRental(long amount) { this.poolRentalAmount = amount; }
    public Long getId() { return id; } public Subscription getSubscription() { return subscription; } public LocalDateTime getStartsAt() { return startsAt; }
}
