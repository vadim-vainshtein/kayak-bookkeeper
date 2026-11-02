package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V5__allow_zero_pricing_rates extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        if ("H2".equals(context.getConnection().getMetaData().getDatabaseProductName())) {
            dropH2AmountConstraint(context);
        } else {
            execute(context, "alter table pricing_rates drop constraint if exists pricing_rates_amount_check");
        }
        execute(context, "alter table pricing_rates add constraint pricing_rates_amount_nonnegative_check check (amount >= 0)");
    }

    private void dropH2AmountConstraint(Context context) throws SQLException {
        try (Statement statement = context.getConnection().createStatement();
             ResultSet constraints = statement.executeQuery("""
                     select tc.constraint_name
                     from information_schema.table_constraints tc
                     join information_schema.check_constraints cc
                       on tc.constraint_catalog = cc.constraint_catalog
                      and tc.constraint_schema = cc.constraint_schema
                      and tc.constraint_name = cc.constraint_name
                     where tc.table_schema = current_schema()
                       and tc.table_name = 'PRICING_RATES'
                       and tc.constraint_type = 'CHECK'
                       and cc.check_clause like '%AMOUNT%>%'
                     """)) {
            while (constraints.next()) {
                String name = constraints.getString(1).replace("\"", "\"\"");
                execute(context, "alter table pricing_rates drop constraint \"" + name + "\"");
            }
        }
    }

    private void execute(Context context, String sql) throws SQLException {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }
}
