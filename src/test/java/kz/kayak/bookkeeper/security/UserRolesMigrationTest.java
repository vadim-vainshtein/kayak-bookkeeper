package kz.kayak.bookkeeper.security;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRolesMigrationTest {
    @Test
    void upgradesExistingAccountsToAdminsWithoutChangingPasswordsAndConstrainsNewRoles() throws Exception {
        String url = "jdbc:h2:mem:role-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("6").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into user_accounts(login, password_hash) values ('existing', 'original-hash')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var result = statement.executeQuery("select role, password_hash from user_accounts where login = 'existing'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("role")).isEqualTo("ADMIN");
                assertThat(result.getString("password_hash")).isEqualTo("original-hash");
            }
            statement.executeUpdate("insert into user_accounts(login, password_hash, role) values ('reader', 'hash', 'READ_ONLY')");
            assertThatThrownBy(() -> statement.executeUpdate("insert into user_accounts(login, password_hash, role) values ('invalid', 'hash', 'OWNER')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("insert into user_accounts(login, password_hash) values ('missing-role', 'hash')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
