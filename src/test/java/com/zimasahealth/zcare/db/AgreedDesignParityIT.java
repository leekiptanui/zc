package com.zimasahealth.zcare.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

/**
 * The database must match the agreed design exactly (roadmap M1). Builds one database from the
 * Liquibase changelog and another from the agreed design script, then requires every schema
 * object, privilege and comment to be identical.
 */
class AgreedDesignParityIT {

    private static final String AGREED_DESIGN = "fixtures/db/zcare-agreed-design.sql";

    @Test
    void changelogBuildsExactlyTheAgreedDesign() throws Exception {
        String viaChangelog = PostgresTestServer.createDatabase("parity_changelog_it", null);
        String viaDesign = PostgresTestServer.createDatabase("parity_agreed_design_it", null);

        try (Connection connection = PostgresTestServer.connectAsSuperuser(viaChangelog)) {
            PostgresTestServer.migrate(connection);
        }
        Container.ExecResult design = PostgresTestServer.runPsqlScript(viaDesign, AGREED_DESIGN);
        assertThat(design.getExitCode()).as("agreed design script failed: %s", design.getStderr()).isZero();

        List<String> changelog;
        List<String> agreed;
        try (Connection a = PostgresTestServer.connectAsSuperuser(viaChangelog);
             Connection b = PostgresTestServer.connectAsSuperuser(viaDesign)) {
            changelog = SchemaCatalogue.describe(a);
            agreed = SchemaCatalogue.describe(b);
        }

        assertThat(agreed.stream().filter(line -> line.startsWith("table "))).hasSize(60);
        assertThat(changelog.stream().filter(line -> !agreed.contains(line)).toList())
                .as("objects the changelog builds that the agreed design does not have").isEmpty();
        assertThat(agreed.stream().filter(line -> !changelog.contains(line)).toList())
                .as("objects in the agreed design that the changelog does not build").isEmpty();
    }
}
