package kz.kayak.bookkeeper.security;

import kz.kayak.bookkeeper.domain.UserAccount;
import kz.kayak.bookkeeper.domain.UserRole;
import kz.kayak.bookkeeper.repository.UserAccountRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class DefaultAdminInitializer {
    @Bean
    ApplicationRunner createInitialAdmin(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                                         @Value("${app.initial-admin-password:}") String initialAdminPassword) {
        return arguments -> {
            if (accounts.findByLogin("admin").isEmpty()) {
                if (initialAdminPassword.length() < 12 || initialAdminPassword.length() > 72) {
                    throw new IllegalStateException("Set APP_INITIAL_ADMIN_PASSWORD to a value between 12 and 72 characters before the first startup");
                }
                accounts.save(new UserAccount("admin", passwordEncoder.encode(initialAdminPassword), UserRole.ADMIN));
            }
        };
    }
}
