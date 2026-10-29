package kz.kayak.bookkeeper.security;

import kz.kayak.bookkeeper.domain.UserAccount;
import kz.kayak.bookkeeper.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String login, String currentPassword, String newPassword) {
        UserAccount account = accounts.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        account.updatePasswordHash(passwordEncoder.encode(newPassword));
    }
}
