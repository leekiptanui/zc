package com.zimasahealth.zcare.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Structural rules the migrated schema must keep: the application role's limits, row-level
 * security on every tenant table, the tenant key on every tenant index (DB-15) and explicit
 * constraint names (DB-12). A new table that breaks one fails here, with its name in the message.
 */
class ApplicationRolePostureIT {

    private static final String DATABASE = "application_role_posture_it";
    // Deliberately without row-level security: registries read before a tenant is known,
    // and the security-event sink, which records events before a tenant is resolved.
    private static final List<String> RLS_EXEMPT = List.of("zc_security_event", "zc_tenant", "zc_tenant_whatsapp", "zc_user");
    private static final List<String> APPEND_ONLY = List.of("zc_assessment_response", "zc_config_history",
            "zc_domain_audit", "zc_plan_review", "zc_provider_action", "zc_report_snapshot",
            "zc_risk_classification", "zc_security_event", "zc_task_assignment_history");

    private static Connection connection;

    @BeforeAll
    static void migrate() throws Exception {
        PostgresTestServer.createDatabase(DATABASE, null);
        connection = PostgresTestServer.connectAsSuperuser(DATABASE);
        PostgresTestServer.migrate(connection);
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        connection.close();
    }

    @Test
    void applicationRoleHasNoElevatedAttributes() throws SQLException {
        assertThat(column("""
                SELECT rolname FROM pg_roles
                 WHERE rolname IN ('zc_app', 'zc_housekeeping')
                   AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolreplication)
                """)).isEmpty();
    }

    @Test
    void applicationRoleOwnsNothingAndDeletesNothing() throws SQLException {
        assertThat(column("""
                SELECT relname FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace AND relname LIKE 'zc\\_%'
                   AND pg_has_role('zc_app', relowner, 'MEMBER')
                """)).as("objects owned by zc_app").isEmpty();
        assertThat(column("""
                SELECT relname FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND relname LIKE 'zc\\_%'
                   AND (has_table_privilege('zc_app', oid, 'DELETE') OR has_table_privilege('zc_app', oid, 'TRUNCATE'))
                """)).as("tables zc_app may delete from").isEmpty();
    }

    @Test
    void appendOnlyTablesRefuseUpdatesByPrivilegeAndByTrigger() throws SQLException {
        assertThat(column("""
                SELECT relname FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND relname LIKE 'zc\\_%'
                   AND NOT has_table_privilege('zc_app', oid, 'UPDATE')
                   AND has_table_privilege('zc_app', oid, 'INSERT')
                """)).as("tables zc_app may insert into but not update").containsExactlyInAnyOrderElementsOf(APPEND_ONLY);
        assertThat(column("""
                SELECT DISTINCT tgrelid::regclass::text FROM pg_trigger
                 WHERE NOT tgisinternal AND tgname LIKE '%\\_append\\_only'
                """)).as("tables with an append-only trigger").containsExactlyInAnyOrderElementsOf(APPEND_ONLY);
    }

    @Test
    void everyTenantTableHasForcedRowLevelSecurityWithAPolicy() throws SQLException {
        assertThat(column("""
                SELECT c.relname FROM pg_class c
                 WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r' AND c.relname LIKE 'zc\\_%'
                   AND NOT (c.relrowsecurity AND c.relforcerowsecurity
                            AND EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid))
                """)).as("tables without enabled, forced row-level security and a policy")
                .containsExactlyInAnyOrderElementsOf(RLS_EXEMPT);
    }

    @Test
    void everyTenantTableCarriesAMandatoryTenantKeyThatLeadsEveryIndex() throws SQLException {
        String tenantTables = """
                SELECT c.oid, c.relname FROM pg_class c
                  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id'
                 WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r' AND c.relname LIKE 'zc\\_%'
                   AND c.relname NOT IN ('zc_security_event', 'zc_tenant_whatsapp', 'zc_user')
                """;

        assertThat(column("WITH t AS (" + tenantTables + ") SELECT relname FROM t"))
                .as("tenant tables").hasSize(56);
        assertThat(column("WITH t AS (" + tenantTables + """
                ) SELECT t.relname FROM t
                    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attname = 'tenant_id'
                   WHERE NOT a.attnotnull
                """)).as("tenant tables whose tenant_id is nullable").isEmpty();
        assertThat(column("WITH t AS (" + tenantTables + """
                ) SELECT t.relname FROM t
                   WHERE NOT EXISTS (
                         SELECT 1 FROM pg_constraint k
                          WHERE k.conrelid = t.oid AND k.contype = 'f'
                            AND k.confrelid = 'zc_tenant'::regclass
                            AND k.conkey = ARRAY[(SELECT attnum FROM pg_attribute
                                                   WHERE attrelid = t.oid AND attname = 'tenant_id')])
                """)).as("tenant tables without a foreign key to zc_tenant").isEmpty();
        assertThat(column("WITH t AS (" + tenantTables + """
                ) SELECT i.indexrelid::regclass::text FROM t
                    JOIN pg_index i ON i.indrelid = t.oid AND NOT i.indisprimary
                   WHERE i.indkey[0] <> (SELECT attnum FROM pg_attribute
                                          WHERE attrelid = t.oid AND attname = 'tenant_id')
                """)).as("tenant-table indexes that do not lead with tenant_id").isEmpty();
    }

    @Test
    void everyConstraintAndIndexIsExplicitlyNamed() throws SQLException {
        assertThat(column("""
                SELECT conname FROM pg_constraint
                 WHERE connamespace = 'public'::regnamespace AND contype <> 'n'
                   AND conrelid::regclass::text LIKE 'zc\\_%'
                   AND conname !~ '^(pk|fk|uq|ck)_zc_'
                """)).as("constraints without a pk_/fk_/uq_/ck_ name").isEmpty();
        assertThat(column("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public' AND tablename LIKE 'zc\\_%'
                   AND indexname !~ '^(pk|uq|ix)_zc_'
                """)).as("indexes without a pk_/uq_/ix_ name").isEmpty();
        assertThat(column("""
                SELECT tgname FROM pg_trigger
                 WHERE NOT tgisinternal AND tgrelid::regclass::text LIKE 'zc\\_%'
                   AND tgname !~ '^trg_zc_'
                """)).as("triggers without a trg_ name").isEmpty();
    }

    private static List<String> column(String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
