package kz.kayak.bookkeeper.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    protected UserAccount() { }

    public UserAccount(String login, String passwordHash, UserRole role) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public UserRole getRole() { return role; }
    public String getLogin() { return login; }
    public String getPasswordHash() { return passwordHash; }
    public void updatePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
