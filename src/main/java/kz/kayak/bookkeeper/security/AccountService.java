package kz.kayak.bookkeeper.security;

import kz.kayak.bookkeeper.domain.UserAccount;
import kz.kayak.bookkeeper.api.dto.CreateUserRequest;
import kz.kayak.bookkeeper.api.dto.UserAccountView;
import kz.kayak.bookkeeper.service.DomainException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional(readOnly = true)
    public UserAccountView currentAccount(String login) {
        return view(accounts.findByLogin(login).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<UserAccountView> listAccounts() {
        return accounts.findAll(Sort.by("login")).stream().map(AccountService::view).toList();
    }

    @Transactional
    public UserAccountView createAccount(CreateUserRequest request) {
        if (accounts.findByLogin(request.login()).isPresent()) {
            throw new DomainException("Пользователь с таким логином уже существует.");
        }
        try {
            return view(accounts.saveAndFlush(new UserAccount(request.login(), passwordEncoder.encode(request.password()), request.role())));
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException("Пользователь с таким логином уже существует.");
        }
    }

    private static UserAccountView view(UserAccount account) {
        return new UserAccountView(account.getId(), account.getLogin(), account.getRole());
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
