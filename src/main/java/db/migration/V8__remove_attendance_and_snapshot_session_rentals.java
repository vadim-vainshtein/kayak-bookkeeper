package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserves financial records and the saved rental total when removing attendance. */
public class V8__remove_attendance_and_snapshot_session_rentals extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table training_sessions add column pool_rental_amount bigint check (pool_rental_amount >= 0)");
        }
        snapshotLegacyRentals(connection);
        try (Statement statement = connection.createStatement()) {
            // Charges, payments and allocations survive; only obsolete attendance metadata is removed.
            statement.execute("alter table charges drop column attendance_id");
            statement.execute("drop table attendance");
            statement.execute("update charges set description = coalesce(description, 'Разовая тренировка'), type = 'MANUAL' where type = 'DROP_IN'");
            statement.execute("delete from pricing_rates where code = 'DROP_IN'");
        }
    }

    private void snapshotLegacyRentals(Connection connection) throws SQLException {
        List<SubscriptionRental> subscriptions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select id, pool_rental_amount from subscriptions where status <> 'DRAFT' and pool_rental_amount is not null")) {
            while (result.next()) subscriptions.add(new SubscriptionRental(result.getLong(1), result.getLong(2)));
        }
        for (SubscriptionRental subscription : subscriptions) {
            List<SessionRental> sessions = sessions(connection, subscription.id());
            if (sessions.isEmpty()) continue;
            List<Long> rates = new ArrayList<>();
            boolean reconstructible = true;
            for (SessionRental session : sessions) {
                Long rate = applicableRate(connection, session.startsAt());
                reconstructible &= rate != null;
                rates.add(rate);
            }
            // Rate history can be edited/deleted. Trust it only if it exactly reproduces the saved total.
            boolean useRates = reconstructible && rates.stream().mapToLong(Long::longValue).sum() == subscription.total();
            try (PreparedStatement update = connection.prepareStatement("update training_sessions set pool_rental_amount = ? where id = ?")) {
                for (int index = 0; index < sessions.size(); index++) {
                    long amount = useRates ? rates.get(index)
                            : subscription.total() / sessions.size() + (index < subscription.total() % sessions.size() ? 1 : 0);
                    update.setLong(1, amount);
                    update.setLong(2, sessions.get(index).id());
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
    }

    private List<SessionRental> sessions(Connection connection, long subscriptionId) throws SQLException {
        List<SessionRental> sessions = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("select id, starts_at from training_sessions where subscription_id = ? order by starts_at, id")) {
            query.setLong(1, subscriptionId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) sessions.add(new SessionRental(result.getLong(1), result.getTimestamp(2).toLocalDateTime()));
            }
        }
        return sessions;
    }

    private Long applicableRate(Connection connection, LocalDateTime startsAt) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select amount from pricing_rates where code = 'POOL_RENTAL' and effective_from <= ? and (effective_to is null or effective_to >= ?) order by effective_from desc, id desc")) {
            query.setObject(1, startsAt.toLocalDate());
            query.setObject(2, startsAt.toLocalDate());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        }
    }

    private record SubscriptionRental(long id, long total) {}
    private record SessionRental(long id, LocalDateTime startsAt) {}
}
