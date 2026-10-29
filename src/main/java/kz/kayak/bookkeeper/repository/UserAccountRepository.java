package kz.kayak.bookkeeper.repository;

import kz.kayak.bookkeeper.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByLogin(String login);
}
