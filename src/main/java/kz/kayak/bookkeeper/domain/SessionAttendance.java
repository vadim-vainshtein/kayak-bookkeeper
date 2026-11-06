package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;

/** A row means this person attended. Absence is the default, with no row. */
@Entity
@Table(name = "session_attendance", uniqueConstraints = @UniqueConstraint(columnNames = {"training_session_id", "person_id"}))
public class SessionAttendance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "training_session_id") private TrainingSession session;
    @ManyToOne(optional = false) private Person person;
    @Column(nullable = false) private boolean guest;
    @OneToOne @JoinColumn(name = "charge_id", unique = true) private Charge charge;

    protected SessionAttendance() {}
    public SessionAttendance(TrainingSession session, Person person, boolean guest) {
        this.session = session;
        this.person = person;
        this.guest = guest;
    }
    public Long getId() { return id; }
    public TrainingSession getSession() { return session; }
    public Person getPerson() { return person; }
    public boolean isGuest() { return guest; }
    public Charge getCharge() { return charge; }
    public void setCharge(Charge charge) { this.charge = charge; }
}
