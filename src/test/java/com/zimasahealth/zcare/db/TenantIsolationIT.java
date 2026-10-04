package com.zimasahealth.zcare.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * One client can never see or write another client's data (roadmap M1). Runs as zc_app, the
 * role the service uses: a test run as the owner or a superuser would pass while proving nothing,
 * because superusers bypass row-level security (04D section 12.4).
 */
class TenantIsolationIT {

    private static final String DATABASE = "tenant_isolation_it";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static long tenantA;
    private static long tenantB;

    private Connection app;

    @BeforeAll
    static void migrateAndSeedTwoTenants() throws Exception {
        PostgresTestServer.createDatabase(DATABASE, null);
        try (Connection superuser = PostgresTestServer.connectAsSuperuser(DATABASE)) {
            PostgresTestServer.migrate(superuser);
            tenantA = insertTenantWithOrganisation(superuser, "a");
            tenantB = insertTenantWithOrganisation(superuser, "b");
        }
    }

    @BeforeEach
    void connectAsApplication() throws SQLException {
        app = PostgresTestServer.connectAsApp(DATABASE);
        app.setAutoCommit(false);
    }

    @AfterEach
    void rollBackAndClose() throws SQLException {
        app.rollback();
        app.close();
    }

    @Test
    void seesOnlyTheRowsOfTheTenantInContext() throws SQLException {
        useTenant(tenantA);

        assertThat(organisationCodes()).containsExactly("org-a");
    }

    @Test
    void seesNothingWhenNoTenantIsSet() throws SQLException {
        assertThat(organisationCodes()).isEmpty();
    }

    @Test
    void cannotWriteARowForAnotherTenant() throws SQLException {
        useTenant(tenantA);

        assertThatThrownBy(() -> insertOrganisation(app, tenantB, "org-b-2"))
                .isInstanceOfSatisfying(SQLException.class, e -> {
                    assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
                    assertThat(e.getMessage()).contains("row-level security");
                });
    }

    @Test
    void cannotSwitchRowLevelSecurityOff() throws SQLException {
        useTenant(tenantA);
        execute("SET LOCAL row_security = off");

        assertThatThrownBy(this::organisationCodes)
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    void cannotDeleteRows() throws SQLException {
        useTenant(tenantA);

        assertThatThrownBy(() -> execute("DELETE FROM zc_organisation WHERE code = 'org-a'"))
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    void cannotRewriteTheAuditTrail() throws SQLException {
        useTenant(tenantA);
        execute("INSERT INTO zc_domain_audit (tenant_id, actor_id, entity_type, entity_id, operation, correlation_id, created_by)"
                + " VALUES (" + tenantA + ", 'it-actor', 'organisation', 1, 'TEST_OPERATION', 'it-correlation', 'it')");

        assertThatThrownBy(() -> execute("UPDATE zc_domain_audit SET reason = 'rewritten'"))
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    void canReadTheTenantRegistryToResolveATenant() throws SQLException {
        List<String> codes = new ArrayList<>();
        try (Statement statement = app.createStatement();
             ResultSet rows = statement.executeQuery("SELECT code FROM zc_tenant ORDER BY code")) {
            while (rows.next()) {
                codes.add(rows.getString(1));
            }
        }

        assertThat(codes).containsExactly("tenant-a", "tenant-b");
    }

    private void useTenant(long tenantId) throws SQLException {
        try (PreparedStatement statement = app.prepareStatement("SELECT set_config('zcare.tenant_id', ?, true)")) {
            statement.setString(1, Long.toString(tenantId));
            statement.execute();
        }
    }

    private List<String> organisationCodes() throws SQLException {
        List<String> codes = new ArrayList<>();
        try (Statement statement = app.createStatement();
             ResultSet rows = statement.executeQuery("SELECT code FROM zc_organisation ORDER BY code")) {
            while (rows.next()) {
                codes.add(rows.getString(1));
            }
        }
        return codes;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = app.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long insertTenantWithOrganisation(Connection superuser, String suffix) throws SQLException {
        long tenantId;
        try (PreparedStatement statement = superuser.prepareStatement(
                "INSERT INTO zc_tenant (code, name, created_by) VALUES (?, ?, 'it') RETURNING id")) {
            statement.setString(1, "tenant-" + suffix);
            statement.setString(2, "[SYNTHETIC] Tenant " + suffix.toUpperCase());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                tenantId = row.getLong(1);
            }
        }
        insertOrganisation(superuser, tenantId, "org-" + suffix);
        return tenantId;
    }

    private static void insertOrganisation(Connection connection, long tenantId, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO zc_organisation (tenant_id, code, name, org_type, created_by) VALUES (?, ?, ?, 'payer', 'it')")) {
            statement.setLong(1, tenantId);
            statement.setString(2, code);
            statement.setString(3, "[SYNTHETIC] " + code);
            statement.executeUpdate();
        }
    }
}
