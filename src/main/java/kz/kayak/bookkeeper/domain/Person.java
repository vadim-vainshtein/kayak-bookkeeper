package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;

@Entity @Table(name = "people")
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String fullName;
    private String phone;
    @Column(length = 1000) private String note;
    @Column(nullable = false) private boolean active = true;
    protected Person() { }
    public Person(String fullName, String phone, String note) { this.fullName = fullName; this.phone = phone; this.note = note; }
    public Long getId() { return id; } public String getFullName() { return fullName; } public String getPhone() { return phone; } public String getNote() { return note; } public boolean isActive() { return active; }
    public void update(String fullName, String phone, String note, boolean active) { this.fullName = fullName; this.phone = phone; this.note = note; this.active = active; }
}
