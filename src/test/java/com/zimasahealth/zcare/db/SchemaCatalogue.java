package com.zimasahealth.zcare.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a normalised, sorted description of every ZCare schema object in one database, so two
 * databases can be compared line by line. Covers tables and their RLS flags, columns, constraints,
 * indexes, triggers, functions, policies, privileges, sequences, comments and extensions.
 * Liquibase's own tracking tables are excluded.
 */
final class SchemaCatalogue {

    private static final List<String> QUERIES = List.of(
            """
            SELECT 'table ' || c.relname || ' rls=' || c.relrowsecurity || ' force=' || c.relforcerowsecurity
              FROM pg_class c
             WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r' AND c.relname LIKE 'zc\\_%'
            """,
            """
            SELECT 'column ' || table_name || '.' || column_name || ' #' || ordinal_position
                   || ' ' || data_type || '/' || udt_name || ' null=' || is_nullable
                   || ' default=' || coalesce(column_default, '-')
                   || ' identity=' || is_identity || '/' || coalesce(identity_generation, '-')
              FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name LIKE 'zc\\_%'
            """,
            """
            SELECT 'constraint ' || conrelid::regclass || '.' || conname || ' ' || contype::text
                   || ' ' || pg_get_constraintdef(oid)
              FROM pg_constraint
             WHERE connamespace = 'public'::regnamespace AND conrelid::regclass::text LIKE 'zc\\_%'
            """,
            """
            SELECT 'index ' || indexname || ' ' || indexdef
              FROM pg_indexes
             WHERE schemaname = 'public' AND tablename LIKE 'zc\\_%'
            """,
            """
            SELECT 'trigger ' || tgname || ' ' || pg_get_triggerdef(oid)
              FROM pg_trigger
             WHERE NOT tgisinternal AND tgrelid::regclass::text LIKE 'zc\\_%'
            """,
            """
            SELECT 'function ' || p.oid::regprocedure || ' ' || pg_get_functiondef(p.oid)
              FROM pg_proc p
             WHERE p.pronamespace = 'public'::regnamespace AND p.proname LIKE 'zc\\_%'
            """,
            """
            SELECT 'policy ' || tablename || '.' || policyname || ' ' || permissive
                   || ' roles=' || array_to_string(roles, ',') || ' cmd=' || cmd
                   || ' using=' || coalesce(qual, '-') || ' check=' || coalesce(with_check, '-')
              FROM pg_policies
             WHERE schemaname = 'public'
            """,
            """
            SELECT 'privilege ' || c.relname || ' ' || coalesce(a.grantee::regrole::text, 'PUBLIC')
                   || ' ' || a.privilege_type
              FROM pg_class c, aclexplode(c.relacl) a
             WHERE c.relnamespace = 'public'::regnamespace AND c.relname LIKE 'zc\\_%'
               AND a.grantee <> c.relowner
            """,
            """
            SELECT 'sequence ' || sequencename || ' ' || data_type || ' start=' || start_value
                   || ' increment=' || increment_by || ' cycle=' || cycle
              FROM pg_sequences
             WHERE schemaname = 'public' AND sequencename LIKE 'zc\\_%'
            """,
            """
            SELECT 'comment ' || c.relname || coalesce('.' || a.attname, '') || ' ' || d.description
              FROM pg_description d
              JOIN pg_class c ON d.classoid = 'pg_class'::regclass AND d.objoid = c.oid
              LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = d.objsubid AND d.objsubid > 0
             WHERE c.relnamespace = 'public'::regnamespace AND c.relname LIKE 'zc\\_%'
            UNION ALL
            SELECT 'comment ' || p.oid::regprocedure || ' ' || d.description
              FROM pg_description d
              JOIN pg_proc p ON d.classoid = 'pg_proc'::regclass AND d.objoid = p.oid
             WHERE p.pronamespace = 'public'::regnamespace AND p.proname LIKE 'zc\\_%'
            """,
            """
            SELECT 'extension ' || extname FROM pg_extension
            """,
            """
            SELECT 'schema public ' || array_to_string(nspacl, ',') FROM pg_namespace WHERE nspname = 'public'
            """);

    private SchemaCatalogue() {
    }

    static List<String> describe(Connection connection) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            for (String query : QUERIES) {
                try (ResultSet rows = statement.executeQuery(query)) {
                    while (rows.next()) {
                        lines.add(rows.getString(1));
                    }
                }
            }
        }
        lines.sort(null);
        return lines;
    }
}
