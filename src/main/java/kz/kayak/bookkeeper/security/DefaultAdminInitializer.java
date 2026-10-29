package kz.kayak.bookkeeper.security;

import kz.kayak.bookkeeper.domain.UserAccount;
import kz.kayak.bookkeeper.repository.UserAccountRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class DefaultAdminInitializer {
    @Bean
    ApplicationRunner createDefaultAdmin(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        return arguments -> {
            if (accounts.findByLogin("admin").isEmpty()) {
                accounts.save(new UserAccount("admin", passwordEncoder.encode("admin")));
            }
        };
    }
}
