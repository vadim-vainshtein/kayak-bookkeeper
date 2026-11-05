package kz.kayak.bookkeeper.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kz.kayak.bookkeeper.api.dto.PasswordChangeRequest;
import kz.kayak.bookkeeper.api.dto.CreateUserRequest;
import kz.kayak.bookkeeper.api.dto.UserAccountView;
import java.util.List;
import kz.kayak.bookkeeper.security.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Учёт тренировок", description = "Абонементы, тарифы и оплаты каяк-клуба")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Получить текущую учётную запись и роль")
    @GetMapping("/account")
    public UserAccountView currentAccount(Authentication authentication) {
        return accountService.currentAccount(authentication.getName());
    }

    @Operation(summary = "Получить учётные записи (только администратор)")
    @GetMapping("/users")
    public List<UserAccountView> users() {
        return accountService.listAccounts();
    }

    @Operation(summary = "Создать учётную запись (только администратор)")
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountView createUser(@Valid @RequestBody CreateUserRequest request) {
        return accountService.createAccount(request);
    }

    @Operation(summary = "Сменить пароль текущего пользователя")
    @PutMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest body) {
        accountService.changePassword(authentication.getName(), body.currentPassword(), body.newPassword());
    }
}
