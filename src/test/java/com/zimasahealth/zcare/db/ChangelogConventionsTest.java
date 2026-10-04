package com.zimasahealth.zcare.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import liquibase.Scope;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Enforces the migration rules of the project-layout standard on the changelog itself, without
 * a database: DB-02 (master holds includes only), DB-03 (file names and order), DB-04 (changeset
 * ids), DB-06 (file header), DB-08 (HALT preconditions), DB-10 (rollbacks) and DB-11 (vendor SQL
 * tagged). A new migration that breaks one of them fails here with its id in the message.
 */
class ChangelogConventionsTest {

    private static final Pattern FILE_NAME = Pattern.compile("db/changelog/migrations/((\\d{8})_(\\d{2})_[a-z0-9]+(?:_[a-z0-9]+)*\\.xml)");
    private static final Pattern CHANGESET_ID = Pattern.compile("(\\d{8})-(\\d{2})-(\\d{2})");

    private static List<String> includes;
    private static List<ChangeSet> changeSets;

    @BeforeAll
    static void parseChangelog() throws Exception {
        includes = readIncludes();
        ResourceAccessor resources = new ClassLoaderResourceAccessor();
        DatabaseChangeLog changeLog = Scope.child(Map.of(Scope.Attr.resourceAccessor.name(), resources),
                () -> ChangeLogParserFactory.getInstance()
                        .getParser(PostgresTestServer.CHANGELOG, resources)
                        .parse(PostgresTestServer.CHANGELOG, new ChangeLogParameters(), resources));
        changeSets = changeLog.getChangeSets();
    }

    @Test
    void masterChangelogIncludesEveryMigrationFileInSequenceOrder() {
        assertThat(includes).isNotEmpty().allSatisfy(path -> assertThat(path).matches(FILE_NAME));

        List<Integer> sequence = includes.stream().map(path -> fileMatcher(path).group(3)).map(Integer::valueOf).toList();
        for (int i = 0; i < sequence.size(); i++) {
            assertThat(sequence.get(i)).as("sequence number of %s", includes.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void everyMigrationFileStartsWithAHeaderNamingItself() throws Exception {
        for (String path : includes) {
            String name = fileMatcher(path).group(1);
            try (InputStream in = resource(path)) {
                String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(xml).as(path).contains("<!-- " + name + " -->").contains("Author:");
            }
        }
    }

    @Test
    void changeSetIdsFollowTheirFileDateAndSequence() {
        Map<String, List<ChangeSet>> byFile = new LinkedHashMap<>();
        changeSets.forEach(cs -> byFile.computeIfAbsent(cs.getFilePath(), k -> new ArrayList<>()).add(cs));

        assertThat(byFile.keySet()).containsExactlyElementsOf(includes);
        byFile.forEach((path, sets) -> {
            Matcher file = fileMatcher(path);
            for (int i = 0; i < sets.size(); i++) {
                ChangeSet cs = sets.get(i);
                Matcher id = CHANGESET_ID.matcher(cs.getId());
                assertThat(id.matches()).as("id %s in %s", cs.getId(), path).isTrue();
                assertThat(id.group(1) + "-" + id.group(2)).as("date and file sequence of %s", cs.getId())
                        .isEqualTo(file.group(2) + "-" + file.group(3));
                assertThat(Integer.parseInt(id.group(3))).as("changeset sequence of %s", cs.getId()).isEqualTo(i + 1);
                assertThat(cs.getAuthor()).as("author of %s", cs.getId()).isNotBlank();
            }
        });
    }

    @Test
    void everyChangeSetHaltsWhenItsPreconditionsFail() {
        assertThat(changeSets).allSatisfy(cs -> {
            PreconditionContainer preconditions = cs.getPreconditions();
            assertThat(preconditions).as("preconditions of %s", cs.getId()).isNotNull();
            assertThat(preconditions.getNestedPreconditions()).as("preconditions of %s", cs.getId()).isNotEmpty();
            assertThat(preconditions.getOnFail()).as("onFail of %s", cs.getId()).isEqualTo(PreconditionContainer.FailOption.HALT);
        });
    }

    @Test
    void everyChangeSetCarriesItsOwnRollback() {
        assertThat(changeSets).filteredOn(cs -> !cs.isAlwaysRun()).allSatisfy(cs ->
                assertThat(cs.getRollback().getChanges()).as("rollback of %s", cs.getId()).isNotEmpty());
    }

    @Test
    void onlyTheReadOnlyPostureCheckRunsAlways() {
        assertThat(changeSets).filteredOn(cs -> cs.isAlwaysRun() || cs.isRunOnChange())
                .extracting(ChangeSet::getId)
                .containsExactly("20260929-20-01");
    }

    @Test
    void everyChangeSetIsPostgresqlSqlThatExplainsItself() {
        assertThat(changeSets).allSatisfy(cs -> {
            assertThat(cs.getDbmsSet()).as("dbms of %s", cs.getId()).containsExactly("postgresql");
            assertThat(cs.getComments()).as("comment of %s", cs.getId()).isNotBlank();
            assertThat(cs.getChanges()).as("changes of %s", cs.getId())
                    .isNotEmpty().allSatisfy(change -> assertThat(change).isInstanceOf(RawSQLChange.class));
        });
    }

    private static List<String> readIncludes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        List<String> files = new ArrayList<>();
        try (InputStream in = resource(PostgresTestServer.CHANGELOG)) {
            NodeList children = factory.newDocumentBuilder().parse(in).getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    assertThat(node.getLocalName()).as("master changelog holds include elements only (DB-02)").isEqualTo("include");
                    files.add(((Element) node).getAttribute("file"));
                }
            }
        }
        return files;
    }

    private static Matcher fileMatcher(String path) {
        Matcher matcher = FILE_NAME.matcher(path);
        assertThat(matcher.matches()).as("migration file name %s (DB-03)", path).isTrue();
        return matcher;
    }

    private static InputStream resource(String path) {
        InputStream in = ChangelogConventionsTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(in).as("classpath resource %s", path).isNotNull();
        return in;
    }
}
