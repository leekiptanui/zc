package com.zimasahealth.zcare.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One PostgreSQL 16 server, started once per test JVM and shared by every database integration
 * test. Each test class works in a database of its own, so no test depends on another test's
 * state or on a shared environment (TST-03).
 */
final class PostgresTestServer {

    static final String CHANGELOG = "db/changelog/changelog-master.xml";
    static final String APP_ROLE = "zc_app";

    private static final String IMAGE = "postgres:16";
    // Generated per run: the server is throwaway and no credential lives in a tracked file (CFG-01).
    private static final String APP_ROLE_PASSWORD = UUID.randomUUID().toString();
    private static final PostgreSQLContainer<?> SERVER = startServer();

    private PostgresTestServer() {
    }

    /** Creates an empty database owned by the given role, or by the superuser when {@code owner} is null. */
    static String createDatabase(String name, String owner) throws SQLException {
        try (Connection connection = connectAsSuperuser("postgres");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name + (owner == null ? "" : " OWNER " + owner));
        }
        return name;
    }

    /** Creates a login role that may create roles but is not a superuser: the production schema owner's shape. */
    static void createOwnerRole(String name, String password) throws SQLException {
        try (Connection connection = connectAsSuperuser("postgres");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + name + " LOGIN CREATEROLE PASSWORD '" + password + "'");
        }
    }

    static Connection connectAsSuperuser(String database) throws SQLException {
        return connect(database, SERVER.getUsername(), SERVER.getPassword());
    }

    /** Connects as zc_app, the role the service uses; its password is set after the roles exist. */
    static Connection connectAsApp(String database) throws SQLException {
        try (Connection connection = connectAsSuperuser(database);
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER ROLE " + APP_ROLE + " PASSWORD '" + APP_ROLE_PASSWORD + "'");
        }
        return connect(database, APP_ROLE, APP_ROLE_PASSWORD);
    }

    static Connection connect(String database, String user, String password) throws SQLException {
        String url = "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + database;
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Applies the master changelog over the given connection, exactly as the service will at startup.
     * Liquibase switches the connection to manual commit; it is switched back, so later statements
     * on the same connection commit as the caller expects.
     */
    static void migrate(Connection connection) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        try {
            Scope.child(Map.of(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor()), () -> {
                new CommandScope(UpdateCommandStep.COMMAND_NAME)
                        .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, database)
                        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG)
                        .execute();
                return null;
            });
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Runs a classpath SQL script through psql inside the server, stopping at the first error. */
    static Container.ExecResult runPsqlScript(String database, String classpathResource) throws Exception {
        String target = "/tmp/" + classpathResource.replace('/', '_');
        SERVER.copyFileToContainer(MountableFile.forClasspathResource(classpathResource), target);
        return SERVER.execInContainer("psql", "-v", "ON_ERROR_STOP=1", "-q",
                "-U", SERVER.getUsername(), "-d", database, "-f", target);
    }

    private static PostgreSQLContainer<?> startServer() {
        PostgreSQLContainer<?> server = new PostgreSQLContainer<>(IMAGE);
        server.start();
        return server;
    }
}
