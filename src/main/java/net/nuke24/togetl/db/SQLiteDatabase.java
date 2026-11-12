package net.nuke24.togetl.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens and initializes the source-agnostic SQLite database.
 *
 * <p>The schema stores statements, provenance, parse issues, and vocabulary
 * terms. Domain views are layered on top of the generic statement table so the
 * loader never needs to know about TEF or Org.</p>
 */
public final class SQLiteDatabase {
    public static final int SCHEMA_VERSION = 1;

    private static final String[] SCHEMA = new String[] {
        "CREATE TABLE IF NOT EXISTS document ("
            + "iri TEXT PRIMARY KEY,"
            + "source_url TEXT NOT NULL,"
            + "sha256 TEXT NOT NULL,"
            + "byte_length INTEGER NOT NULL,"
            + "detected_format TEXT,"
            + "parser_version TEXT"
            + ")",

        "CREATE TABLE IF NOT EXISTS batch ("
            + "iri TEXT PRIMARY KEY,"
            + "started_at TEXT NOT NULL,"
            + "importer_version TEXT NOT NULL,"
            + "options_json TEXT NOT NULL"
            + ")",

        "CREATE TABLE IF NOT EXISTS loading ("
            + "iri TEXT PRIMARY KEY,"
            + "batch_iri TEXT NOT NULL REFERENCES batch(iri),"
            + "document_iri TEXT NOT NULL REFERENCES document(iri)"
            + ")",

        "CREATE TABLE IF NOT EXISTS statement ("
            + "id INTEGER PRIMARY KEY,"
            + "source TEXT NOT NULL,"
            + "line INTEGER,"
            + "column INTEGER,"
            + "end_line INTEGER,"
            + "end_column INTEGER,"
            + "subject TEXT NOT NULL,"
            + "predicate TEXT NOT NULL,"
            + "object TEXT NOT NULL,"
            + "object_kind TEXT NOT NULL,"
            + "datatype TEXT,"
            + "batch_iri TEXT,"
            + "loading_iri TEXT"
            + ")",

        "CREATE INDEX IF NOT EXISTS statement_subject ON statement(subject)",
        "CREATE INDEX IF NOT EXISTS statement_predicate ON statement(predicate)",
        "CREATE INDEX IF NOT EXISTS statement_object ON statement(object)",
        "CREATE INDEX IF NOT EXISTS statement_subject_predicate"
            + " ON statement(subject, predicate)",
        "CREATE INDEX IF NOT EXISTS statement_source ON statement(source)",
        "CREATE INDEX IF NOT EXISTS statement_batch ON statement(batch_iri)",

        "CREATE TABLE IF NOT EXISTS parse_issue ("
            + "id INTEGER PRIMARY KEY,"
            + "document_iri TEXT NOT NULL,"
            + "line INTEGER,"
            + "column INTEGER,"
            + "severity TEXT NOT NULL,"
            + "code TEXT NOT NULL,"
            + "message TEXT NOT NULL,"
            + "raw TEXT,"
            + "batch_iri TEXT,"
            + "loading_iri TEXT"
            + ")",

        "CREATE INDEX IF NOT EXISTS parse_issue_document ON parse_issue(document_iri)",
        "CREATE INDEX IF NOT EXISTS parse_issue_batch ON parse_issue(batch_iri)",

        "CREATE TABLE IF NOT EXISTS vocab_term ("
            + "iri TEXT PRIMARY KEY,"
            + "kind TEXT,"
            + "label TEXT,"
            + "comment TEXT,"
            + "status TEXT,"
            + "defined_in TEXT"
            + ")",

        "CREATE TABLE IF NOT EXISTS import_report_row ("
            + "id INTEGER PRIMARY KEY,"
            + "batch_iri TEXT,"
            + "document_iri TEXT,"
            + "kind TEXT NOT NULL,"
            + "entry_type TEXT,"
            + "field TEXT,"
            + "occurrence_count INTEGER NOT NULL,"
            + "first_location TEXT"
            + ")",

        "CREATE INDEX IF NOT EXISTS import_report_batch"
            + " ON import_report_row(batch_iri)",
        "CREATE INDEX IF NOT EXISTS import_report_kind"
            + " ON import_report_row(kind)"
    };

    private static final String[] VIEWS = new String[] {
        "DROP VIEW IF EXISTS conflict",
        "CREATE VIEW conflict AS"
            + " SELECT id, batch_iri, document_iri, kind, entry_type, field,"
            + " occurrence_count, first_location"
            + " FROM import_report_row WHERE kind = 'conflict'",

        "DROP VIEW IF EXISTS part",
        "CREATE VIEW part AS"
            + " SELECT p.subject AS iri,"
            + " (SELECT s.object FROM statement s WHERE s.subject = p.subject"
            + "  AND s.predicate = 'tag:nuke24.net,2026-11:togetl:name'"
            + "  AND s.object_kind = 'literal' ORDER BY s.id LIMIT 1) AS name,"
            + " (SELECT s.object FROM statement s WHERE s.subject = p.subject"
            + "  AND s.predicate = 'tag:nuke24.net,2026-11:togetl:shortDescription'"
            + "  AND s.object_kind = 'literal' ORDER BY s.id LIMIT 1) AS short_description,"
            + " (SELECT s.object FROM statement s WHERE s.subject = p.subject"
            + "  AND s.predicate = 'tag:nuke24.net,2026-11:togetl:created'"
            + "  AND s.object_kind = 'literal' ORDER BY s.id LIMIT 1) AS created"
            + " FROM (SELECT DISTINCT subject FROM statement"
            + "  WHERE predicate = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'"
            + "  AND object = 'tag:nuke24.net,2026-11:togetl:Part') AS p",

        "DROP VIEW IF EXISTS part_file",
        "CREATE VIEW part_file AS"
            + " SELECT s.subject AS part_iri, s.predicate AS relation,"
            + " s.object AS file_iri,"
            + " (SELECT f.object FROM statement f WHERE f.subject = s.object"
            + "  AND f.predicate = 'tag:nuke24.net,2026-11:togetl:filename'"
            + "  AND f.object_kind = 'literal' ORDER BY f.id LIMIT 1) AS filename"
            + " FROM statement s WHERE s.object_kind = 'iri' AND s.predicate IN ("
            + " 'tag:nuke24.net,2026-11:togetl:stlFile',"
            + " 'tag:nuke24.net,2026-11:togetl:csgFile',"
            + " 'tag:nuke24.net,2026-11:togetl:file',"
            + " 'tag:nuke24.net,2026-11:togetl:imageRef',"
            + " 'tag:nuke24.net,2026-11:togetl:photoRef',"
            + " 'tag:nuke24.net,2026-11:togetl:docRef',"
            + " 'tag:nuke24.net,2026-11:togetl:demoRef',"
            + " 'tag:nuke24.net,2026-11:togetl:openscadRenderingRef',"
            + " 'tag:nuke24.net,2026-11:togetl:openscadRendering',"
            + " 'tag:nuke24.net,2026-11:togetl:openscadDesignRef')"
            + " AND s.subject IN (SELECT subject FROM statement"
            + "  WHERE predicate = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'"
            + "  AND object = 'tag:nuke24.net,2026-11:togetl:Part')",

        "DROP VIEW IF EXISTS part_alias",
        "CREATE VIEW part_alias AS"
            + " SELECT subject AS part_iri, object AS alias"
            + " FROM statement WHERE predicate = 'tag:nuke24.net,2026-11:togetl:alias'"
            + " AND object_kind = 'literal'"
            + " AND subject IN (SELECT subject FROM statement"
            + "  WHERE predicate = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'"
            + "  AND object = 'tag:nuke24.net,2026-11:togetl:Part')"
    };

    private SQLiteDatabase() {
    }

    public static Connection open(Path path) throws SQLException, IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        loadDriver();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("PRAGMA foreign_keys = ON");
            statement.executeUpdate("PRAGMA busy_timeout = 5000");
        } finally {
            statement.close();
        }
        return connection;
    }

    public static void initialize(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            for (String sql : SCHEMA) {
                statement.executeUpdate(sql);
            }
            for (String sql : VIEWS) {
                statement.executeUpdate(sql);
            }
            statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
        } finally {
            statement.close();
        }
    }

    /** Deletes all rows while keeping the schema. */
    public static void reset(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("DELETE FROM import_report_row");
            statement.executeUpdate("DELETE FROM statement");
            statement.executeUpdate("DELETE FROM parse_issue");
            statement.executeUpdate("DELETE FROM loading");
            statement.executeUpdate("DELETE FROM batch");
            statement.executeUpdate("DELETE FROM document");
            statement.executeUpdate("DELETE FROM vocab_term");
        } finally {
            statement.close();
        }
    }

    private static void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "SQLite JDBC driver not found; run scripts/get-sqlite-jdbc.sh", e);
        }
    }
}
