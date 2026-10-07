package com.zimasahealth.zcare.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A PostgreSQL 16 server for API integration tests, started once per test JVM. Each test class
 * gets its own database, owned by its own schema-owner role, so the service runs exactly as
 * deployed: Liquibase as the owner, the application as {@code zc_app}.
 *
 * <p>By default the server is a Testcontainers container. To run without Docker, point the tests
 * at an existing PostgreSQL 16 with {@code ZCARE_TEST_PG_HOST}, {@code ZCARE_TEST_PG_PORT},
 * {@code ZCARE_TEST_PG_USER} and {@code ZCARE_TEST_PG_PASSWORD} (a superuser; it creates roles
 * and databases).
 */
public final class ApiTestDatabase {

    public static final String APP_ROLE = "zc_app";
    private static final String APP_PASSWORD = UUID.randomUUID().toString();
    private static final Server SERVER = startServer();

    private final String name;
    private final String owner;
    private final String ownerPassword;

    private ApiTestDatabase(String name, String owner, String ownerPassword) {
        this.name = name;
        this.owner = owner;
        this.ownerPassword = ownerPassword;
    }

    /** A fresh database with its own owner role, and {@code zc_app} able to log in. */
    public static ApiTestDatabase create(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = (prefix + "_" + suffix).toLowerCase(Locale.ROOT);
        String owner = name + "_owner";
        String ownerPassword = UUID.randomUUID().toString();
        try (Connection connection = SERVER.connect("postgres"); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + owner + " LOGIN CREATEROLE PASSWORD '" + ownerPassword + "'");
            statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_ROLE
                    + "') THEN CREATE ROLE " + APP_ROLE + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION"
                    + " NOBYPASSRLS NOINHERIT; END IF; END $$");
            statement.execute("ALTER ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute("CREATE DATABASE " + name + " OWNER " + owner);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create test database " + name, e);
        }
        return new ApiTestDatabase(name, owner, ownerPassword);
    }

    public String jdbcUrl() {
        return SERVER.url(name);
    }

    public String owner() {
        return owner;
    }

    public String ownerPassword() {
        return ownerPassword;
    }

    public String appPassword() {
        return APP_PASSWORD;
    }

    /** Registers a tenant, as the schema owner must: {@code zc_app} can only read {@code zc_tenant}. */
    public long ensureTenant(String code) {
        try (Connection connection = SERVER.connect(name);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO zc_tenant (code, name, created_by) VALUES (?, ?, 'test') ON CONFLICT (code) DO NOTHING");
             PreparedStatement select = connection.prepareStatement("SELECT id FROM zc_tenant WHERE code = ?")) {
            insert.setString(1, code);
            insert.setString(2, code + " (synthetic)");
            insert.executeUpdate();
            select.setString(1, code);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not register tenant " + code, e);
        }
    }

    /** Runs a query as the superuser, scoped to a tenant, and returns the first column of the first row. */
    public long count(long tenantId, String sql) {
        try (Connection connection = SERVER.connect(name); Statement statement = connection.createStatement()) {
            statement.execute("SELECT set_config('zcare.tenant_id', '" + tenantId + "', false)");
            try (ResultSet rs = statement.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
    }

    private static Server startServer() {
        String host = System.getenv("ZCARE_TEST_PG_HOST");
        if (host != null && !host.isBlank()) {
            return new Server(host, Integer.parseInt(System.getenv().getOrDefault("ZCARE_TEST_PG_PORT", "5432")),
                    System.getenv().getOrDefault("ZCARE_TEST_PG_USER", "postgres"),
                    System.getenv().getOrDefault("ZCARE_TEST_PG_PASSWORD", ""));
        }
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        return new Server(container.getHost(), container.getMappedPort(5432), container.getUsername(),
                container.getPassword());
    }

    private record Server(String host, int port, String user, String password) {

        String url(String database) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        Connection connect(String database) throws SQLException {
            return DriverManager.getConnection(url(database), user, password);
        }
    }
}
