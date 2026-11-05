package kz.kayak.bookkeeper.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceRemovalMigrationTest {
    @Test
    void upgradesRecordedAttendanceWithoutLosingFinancialRecordsAndReconstructsRentals() throws Exception {
        String url = "jdbc:h2:mem:attendance-removal-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("7").load().migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("insert into people(id, full_name) values (1, 'Каякер')");
            statement.execute("insert into subscriptions(id, status, capacity, pool_rental_amount) values (1, 'FINALIZED', 1, 300), (2, 'COMPLETED', 1, 101), (3, 'DRAFT', 1, null), (4, 'FINALIZED', 1, 99)");
            statement.execute("insert into training_sessions(id, subscription_id, starts_at) values (1, 1, '2030-01-01 18:00:00'), (2, 1, '2030-01-02 18:00:00'), (3, 2, '2030-01-01 18:00:00'), (4, 2, '2030-01-02 18:00:00'), (5, 3, '2030-01-01 18:00:00'), (6, 4, '2040-01-01 18:00:00'), (7, 4, '2040-01-02 18:00:00')");
            statement.execute("insert into subscription_members(id, subscription_id, person_id) values (1, 1, 1)");
            statement.execute("insert into attendance(id, training_session_id, person_id, subscription_member_id, type, status) values (1, 1, 1, 1, 'SUBSCRIPTION', 'ABSENT')");
            statement.execute("insert into charges(id, person_id, subscription_id, attendance_id, type, amount, charged_at) values (1, 1, 1, 1, 'DROP_IN', 60, '2030-01-01')");
            statement.execute("insert into payments(id, person_id, amount, paid_at, method) values (1, 1, 50, '2030-01-01', 'CASH')");
            statement.execute("insert into payment_allocations(id, payment_id, charge_id, amount, allocated_at) values (1, 1, 1, 50, '2030-01-01')");
            statement.execute("insert into pricing_rates(code, amount, effective_from, effective_to) values ('POOL_RENTAL', 100, '2030-01-01', '2030-01-01'), ('POOL_RENTAL', 200, '2030-01-02', '2030-01-02'), ('DROP_IN', 60, '2030-01-01', null)");

            Flyway.configure().dataSource(url, "sa", "").load().migrate();

            assertThat(rentals(connection, 1)).containsExactly(100L, 200L);
            assertThat(rentals(connection, 2)).containsExactly(51L, 50L); // Changed rates cannot rewrite a saved total.
            assertThat(rentals(connection, 3)).containsExactly((Long) null);
            assertThat(rentals(connection, 4)).containsExactly(50L, 49L); // No historical rates remain.
            try (var result = statement.executeQuery("select c.type, c.amount, c.description, p.amount, a.amount from charges c join payment_allocations a on a.charge_id = c.id join payments p on p.id = a.payment_id")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("MANUAL");
                assertThat(result.getLong(2)).isEqualTo(60);
                assertThat(result.getString(3)).isEqualTo("Разовая тренировка");
                assertThat(result.getLong(4)).isEqualTo(50);
                assertThat(result.getLong(5)).isEqualTo(50);
            }
            try (var result = statement.executeQuery("select count(*) from pricing_rates where code = 'DROP_IN'")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
            try (var tables = connection.getMetaData().getTables(null, null, "ATTENDANCE", null)) {
                assertThat(tables.next()).isFalse();
            }
            // Legacy records no longer prevent schedule/member edits.
            statement.execute("delete from training_sessions where subscription_id = 1");
            statement.execute("delete from subscription_members where subscription_id = 1");
        }
    }

    private List<Long> rentals(Connection connection, long subscriptionId) throws Exception {
        List<Long> amounts = new ArrayList<>();
        try (var query = connection.prepareStatement("select pool_rental_amount from training_sessions where subscription_id = ? order by starts_at, id")) {
            query.setLong(1, subscriptionId);
            try (var result = query.executeQuery()) {
                while (result.next()) amounts.add(result.getObject(1, Long.class));
            }
        }
        return amounts;
    }
}
