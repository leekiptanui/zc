package com.zimasahealth.zcare.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Applies the changelog to an empty database the way production will: as a schema owner that is
 * not a superuser. Proves the migrations need no elevated rights and that re-running them is safe.
 */
class ChangelogMigrationIT {

    private static final String DATABASE = "changelog_migration_it";
    private static final String OWNER = "zcare_owner_migration_it";
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String POSTURE_CHANGESET = "20260929-20-01";

    private static Connection owner;

    @BeforeAll
    static void migrateAsSchemaOwner() throws Exception {
        PostgresTestServer.createOwnerRole(OWNER, OWNER_PASSWORD);
        PostgresTestServer.createDatabase(DATABASE, OWNER);
        owner = PostgresTestServer.connect(DATABASE, OWNER, OWNER_PASSWORD);
        PostgresTestServer.migrate(owner);
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        owner.close();
    }

    @Test
    void createsAllSixtyTablesOwnedByTheSchemaOwner() throws SQLException {
        List<String> owners = column("""
                SELECT DISTINCT pg_get_userbyid(relowner) FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND relname LIKE 'zc\\_%'
                """);

        assertThat(column("""
                SELECT relname FROM pg_class
                 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND relname LIKE 'zc\\_%'
                """)).hasSize(60);
        assertThat(owners).containsExactly(OWNER);
    }

    @Test
    void recordsEachChangeSetExactlyOnce() throws SQLException {
        assertThat(column("SELECT id FROM databasechangelog")).doesNotHaveDuplicates().isNotEmpty();
    }

    @Test
    void aSecondUpdateChangesNothing() throws Exception {
        List<String> schemaBefore = SchemaCatalogue.describe(owner);
        List<String> changeSetsBefore = column("SELECT id FROM databasechangelog ORDER BY id");

        PostgresTestServer.migrate(owner);

        assertThat(SchemaCatalogue.describe(owner)).containsExactlyElementsOf(schemaBefore);
        assertThat(column("SELECT id FROM databasechangelog ORDER BY id")).containsExactlyElementsOf(changeSetsBefore);
        assertThat(column("SELECT id FROM databasechangelog WHERE exectype = 'RERAN'"))
                .as("only the read-only posture checks run again")
                .containsExactly(POSTURE_CHANGESET);
    }

    private static List<String> column(String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = owner.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
