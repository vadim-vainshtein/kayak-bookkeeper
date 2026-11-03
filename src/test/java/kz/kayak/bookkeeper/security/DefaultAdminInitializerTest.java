package kz.kayak.bookkeeper.security;

import kz.kayak.bookkeeper.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminInitializerTest {
    private final DefaultAdminInitializer initializer = new DefaultAdminInitializer();
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);

    @Test
    void refusesToCreateAnAdminWithoutAStrongConfiguredPassword() {
        when(accounts.findByLogin("admin")).thenReturn(java.util.Optional.empty());
        ApplicationRunner runner = initializer.createInitialAdmin(accounts, new BCryptPasswordEncoder(), "short");

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_INITIAL_ADMIN_PASSWORD");
    }
}
