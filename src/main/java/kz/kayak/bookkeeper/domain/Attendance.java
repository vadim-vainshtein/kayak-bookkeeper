package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;

@Entity @Table(name = "attendance", uniqueConstraints = @UniqueConstraint(columnNames = {"training_session_id", "person_id"}))
public class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private TrainingSession trainingSession;
    @ManyToOne(optional = false) private Person person;
    @ManyToOne private SubscriptionMember subscriptionMember;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttendanceType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttendanceStatus status;
    protected Attendance() { }
    public Attendance(TrainingSession trainingSession, Person person, SubscriptionMember subscriptionMember, AttendanceType type, AttendanceStatus status) { this.trainingSession=trainingSession; this.person=person; this.subscriptionMember=subscriptionMember; this.type=type; this.status=status; }
    public Long getId() { return id; } public TrainingSession getTrainingSession() { return trainingSession; } public Person getPerson() { return person; } public SubscriptionMember getSubscriptionMember() { return subscriptionMember; } public AttendanceType getType() { return type; } public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status=status; }
}
