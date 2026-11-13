package net.nuke24.togetl.db;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.vocab.Vocab;
import net.nuke24.togetl.vocab.Vocabulary;

/**
 * Loads TOGETLTSV statements into the generic SQLite schema.
 *
 * <p>The loader derives document metadata and parse issues from the stream
 * itself, so it can load streams produced by importers other than the built-in
 * TEF reader.</p>
 */
public final class Loader {
	public static final String LOADER_VERSION = "togetl-db-loader/1";
	
	private final Connection connection;
	private final boolean simpleProvenance;
	
	public Loader(Connection connection, boolean simpleProvenance) {
		if (connection == null) {
			throw new IllegalArgumentException("Connection must not be null");
		}
		this.connection = connection;
		this.simpleProvenance = simpleProvenance;
	}
	
	public LoadResult load(
			List<LoadedStream> streams,
			List<ConflictRow> reportRows,
			Vocabulary vocabulary
	) throws SQLException {
		if (streams == null || streams.isEmpty()) {
			throw new IllegalArgumentException("At least one stream is required");
		}
		
		String batchIri = simpleProvenance ? null : computeBatchIri(streams);
		boolean replaced = false;
		if (batchIri != null) {
			replaced = deleteBatch(batchIri);
			insertBatch(batchIri);
		}
		
		Map<String, DocumentMeta> documents = collectDocuments(streams);
		insertDocuments(documents);
		Map<String, String> documentByDocumentImport = collectDocumentByDocumentImport(streams, documents);
		
		Map<String, String> loadingByDocument = new LinkedHashMap<String, String>();
		if (batchIri != null) {
			insertLoadings(batchIri, documents.keySet(), loadingByDocument);
		}
		Map<String, String> loadingBySource = loadingBySource(
				documents.keySet(),
				documentByDocumentImport,
				loadingByDocument);
				
		int statementCount = insertStatements(streams, batchIri, loadingBySource);
		int issueCount = insertParseIssues(
				streams, batchIri, loadingBySource, documents, documentByDocumentImport);
		int reportCount = insertReportRows(reportRows, batchIri, documents.keySet());
		int vocabCount = replaceVocabulary(vocabulary);
		
		return new LoadResult(
				batchIri,
				new ArrayList<String>(documents.keySet()),
				statementCount,
				issueCount,
				reportCount,
				vocabCount,
				replaced);
	}
	
	private static String computeBatchIri(List<LoadedStream> streams) {
		List<String> hashes = new ArrayList<String>();
		for (LoadedStream stream : streams) {
			hashes.add(stream.getSha256());
		}
		Collections.sort(hashes);
		StringBuilder descriptor = new StringBuilder();
		descriptor.append(LOADER_VERSION).append('\n');
		descriptor.append("simpleProvenance=false\n");
		for (String hash : hashes) {
			descriptor.append("stream=").append(hash).append('\n');
		}
		return Identifiers.mint(
				"batch",
				Identifiers.shortSha256(descriptor.toString().getBytes(StandardCharsets.UTF_8)));
	}
	
	private boolean deleteBatch(String batchIri) throws SQLException {
		PreparedStatement exists = connection.prepareStatement(
				"SELECT 1 FROM batch WHERE iri = ?");
		try {
			exists.setString(1, batchIri);
			ResultSet rs = exists.executeQuery();
			try {
				if (!rs.next()) {
					return false;
				}
			} finally {
				rs.close();
			}
		} finally {
			exists.close();
		}
		
		execute("DELETE FROM import_report_row WHERE batch_iri = ?", batchIri);
		execute("DELETE FROM statement WHERE batch_iri = ?", batchIri);
		execute("DELETE FROM parse_issue WHERE batch_iri = ?", batchIri);
		execute("DELETE FROM loading WHERE batch_iri = ?", batchIri);
		execute("DELETE FROM batch WHERE iri = ?", batchIri);
		return true;
	}
	
	private void execute(String sql, String value) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			statement.setString(1, value);
			statement.executeUpdate();
		} finally {
			statement.close();
		}
	}
	
	private void insertBatch(String batchIri) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO batch (iri, started_at, importer_version, options_json)"
						+ " VALUES (?, ?, ?, ?)");
		try {
			statement.setString(1, batchIri);
			statement.setString(2, Instant.now().toString());
			statement.setString(3, LOADER_VERSION);
			statement.setString(4, "{\"simpleProvenance\":false}");
			statement.executeUpdate();
		} finally {
			statement.close();
		}
	}
	
	private Map<String, DocumentMeta> collectDocuments(List<LoadedStream> streams) {
		Map<String, DocumentMeta> documents = new LinkedHashMap<String, DocumentMeta>();
		Map<String, String> documentByDocumentImport = new LinkedHashMap<String, String>();
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				if (Vocab.RDF_TYPE.equals(statement.getPredicate())
						&& statement.getObject() instanceof IRI
						&& Vocab.TOGETL_DOCUMENT.toString().equals(statement.getObject().toString())
						&& statement.getSubject() instanceof IRI) {
					String iri = statement.getSubject().toString();
					if (!documents.containsKey(iri)) {
						documents.put(iri, new DocumentMeta(iri));
					}
				}
			}
		}
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				if (Vocab.TOGETL_SOURCE_DOCUMENT.equals(statement.getPredicate())
						&& statement.getSubject() instanceof IRI
						&& statement.getObject() instanceof IRI) {
					documentByDocumentImport.put(
							statement.getSubject().toString(),
							statement.getObject().toString());
				}
			}
		}
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				DocumentMeta meta = documents.get(statement.getSubject().toString());
				if (meta == null) {
					String mappedDocument = documentByDocumentImport.get(statement.getSubject().toString());
					if (mappedDocument != null) {
						meta = documents.get(mappedDocument);
					}
				}
				if (meta == null) {
					continue;
				}
				if (Vocab.TOGETL_SOURCE_URL.equals(statement.getPredicate())) {
					meta.sourceUrl = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_SHA256.equals(statement.getPredicate())) {
					meta.sha256 = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_BYTE_LENGTH.equals(statement.getPredicate())) {
					meta.byteLength = parseLong(literalValue(statement.getObject()));
				} else if (Vocab.TOGETL_FORMAT.equals(statement.getPredicate())) {
					meta.format = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_PARSER_VERSION.equals(statement.getPredicate())) {
					meta.parserVersion = literalValue(statement.getObject());
				}
			}
		}
		
		Set<String> documentImportIris = new HashSet<String>();
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				if (Vocab.TOGETL_SOURCE_DOCUMENT.equals(statement.getPredicate())
						&& statement.getSubject() instanceof IRI) {
					documentImportIris.add(statement.getSubject().toString());
				}
			}
		}
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				String source = statement.getSource();
				if ("-".equals(source)
						|| documents.containsKey(source)
						|| documentImportIris.contains(source)) {
					continue;
				}
				DocumentMeta meta = new DocumentMeta(source);
				meta.sourceUrl = source;
				documents.put(source, meta);
			}
		}
		
		for (DocumentMeta meta : documents.values()) {
			if (meta.sourceUrl == null) {
				meta.sourceUrl = meta.iri;
			}
			if (meta.sha256 == null) {
				meta.sha256 = "";
			}
		}
		return documents;
	}
	
	private Map<String, String> collectDocumentByDocumentImport(
			List<LoadedStream> streams,
			Map<String, DocumentMeta> documents
	) {
		Map<String, String> documentByDocumentImport = new LinkedHashMap<String, String>();
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				String source = statement.getSource();
				if (!"-".equals(source)
						&& documents.containsKey(source)
						&& !documentByDocumentImport.containsKey(source)) {
					documentByDocumentImport.put(source, source);
				}
			}
		}
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				if (!(statement.getSubject() instanceof IRI)
						|| !(statement.getObject() instanceof IRI)
						|| !Vocab.TOGETL_SOURCE_DOCUMENT.equals(statement.getPredicate())) {
					continue;
				}
				String runIri = statement.getSubject().toString();
				String documentIri = statement.getObject().toString();
				if (documents.containsKey(documentIri)) {
					documentByDocumentImport.put(runIri, documentIri);
				}
			}
		}
		
		return documentByDocumentImport;
	}
	
	private static Map<String, String> loadingBySource(
			Iterable<String> documentIris,
			Map<String, String> documentByDocumentImport,
			Map<String, String> loadingByDocument
	) {
		Map<String, String> loadingBySource = new LinkedHashMap<String, String>();
		for (String documentIri : documentIris) {
			loadingBySource.put(documentIri, loadingByDocument.get(documentIri));
		}
		for (Map.Entry<String, String> entry : documentByDocumentImport.entrySet()) {
			loadingBySource.put(entry.getKey(), loadingByDocument.get(entry.getValue()));
		}
		return loadingBySource;
	}
	
	private void insertDocuments(Map<String, DocumentMeta> documents) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO document"
						+ " (iri, source_url, sha256, byte_length, detected_format, parser_version)"
						+ " VALUES (?, ?, ?, ?, ?, ?)"
						+ " ON CONFLICT(iri) DO UPDATE SET"
						+ " source_url = excluded.source_url,"
						+ " sha256 = excluded.sha256,"
						+ " byte_length = excluded.byte_length,"
						+ " detected_format = excluded.detected_format,"
						+ " parser_version = excluded.parser_version");
		try {
			for (DocumentMeta meta : documents.values()) {
				statement.setString(1, meta.iri);
				statement.setString(2, meta.sourceUrl);
				statement.setString(3, meta.sha256);
				statement.setLong(4, meta.byteLength);
				setNullableString(statement, 5, meta.format);
				setNullableString(statement, 6, meta.parserVersion);
				statement.executeUpdate();
			}
		} finally {
			statement.close();
		}
	}
	
	private void insertLoadings(
			String batchIri,
			Iterable<String> documentIris,
			Map<String, String> loadingByDocument
	) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO loading (iri, batch_iri, document_iri) VALUES (?, ?, ?)");
		try {
			for (String documentIri : documentIris) {
				String loadingIri = Identifiers.mint(
						"loading",
						Identifiers.shortSha256(
								(batchIri + "\n" + documentIri)
										.getBytes(StandardCharsets.UTF_8)));
				statement.setString(1, loadingIri);
				statement.setString(2, batchIri);
				statement.setString(3, documentIri);
				statement.executeUpdate();
				loadingByDocument.put(documentIri, loadingIri);
			}
		} finally {
			statement.close();
		}
	}
	
	private int insertStatements(
			List<LoadedStream> streams,
			String batchIri,
			Map<String, String> loadingBySource
	) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO statement"
						+ " (source, line, column, end_line, end_column,"
						+ " subject, predicate, object, object_kind, datatype,"
						+ " batch_iri, loading_iri)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		int count = 0;
		try {
			for (LoadedStream stream : streams) {
				for (Statement sourceStatement : stream.getStatements()) {
					String source = sourceStatement.getSource();
					Location location = sourceStatement.getLocation();
					statement.setString(1, source);
					if (location.isLineColumn()) {
						statement.setInt(2, location.getLine());
						statement.setInt(3, location.getColumn());
						if (location.hasSpan()) {
							statement.setInt(4, location.getEndLine());
							statement.setInt(5, location.getEndColumn());
						} else {
							statement.setNull(4, Types.INTEGER);
							statement.setNull(5, Types.INTEGER);
						}
					} else {
						statement.setNull(2, Types.INTEGER);
						statement.setNull(3, Types.INTEGER);
						statement.setNull(4, Types.INTEGER);
						statement.setNull(5, Types.INTEGER);
					}
					statement.setString(6, sourceStatement.getSubject().toString());
					statement.setString(7, sourceStatement.getPredicate().toString());
					
					Node object = sourceStatement.getObject();
					if (object instanceof Literal) {
						Literal literal = (Literal) object;
						statement.setString(8, literal.getValue());
						statement.setString(9, "literal");
						setNullableString(statement, 10,
								literal.getDatatype() == null
										? null
										: literal.getDatatype().toString());
					} else if (object instanceof BlankNode) {
						statement.setString(8, object.toString());
						statement.setString(9, "blank");
						statement.setNull(10, Types.VARCHAR);
					} else {
						statement.setString(8, object.toString());
						statement.setString(9, "iri");
						statement.setNull(10, Types.VARCHAR);
					}
					
					setNullableString(statement, 11, batchIri);
					setNullableString(statement, 12, loadingBySource.get(source));
					statement.executeUpdate();
					count++;
				}
			}
		} finally {
			statement.close();
		}
		return count;
	}
	
	private int insertParseIssues(
			List<LoadedStream> streams,
			String batchIri,
			Map<String, String> loadingBySource,
			Map<String, DocumentMeta> documents,
			Map<String, String> documentByDocumentImport
	) throws SQLException {
		Map<String, IssueMeta> issues = new LinkedHashMap<String, IssueMeta>();
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				if (Vocab.TOGETL_PARSE_ISSUE_LINK.equals(statement.getPredicate())
						&& statement.getObject() instanceof BlankNode
						&& statement.getSubject() instanceof IRI) {
					String key = statement.getObject().toString();
					if (!issues.containsKey(key)) {
						issues.put(key, new IssueMeta(
								statement.getSubject().toString(),
								statement.getSource(),
								statement.getLocation()));
					}
				}
			}
		}
		
		for (LoadedStream stream : streams) {
			for (Statement statement : stream.getStatements()) {
				IssueMeta meta = issues.get(statement.getSubject().toString());
				if (meta == null) {
					continue;
				}
				if (Vocab.TOGETL_SEVERITY.equals(statement.getPredicate())) {
					meta.severity = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_CODE.equals(statement.getPredicate())) {
					meta.code = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_MESSAGE.equals(statement.getPredicate())) {
					meta.message = literalValue(statement.getObject());
				} else if (Vocab.TOGETL_RAW.equals(statement.getPredicate())) {
					meta.raw = literalValue(statement.getObject());
				}
			}
		}
		
		PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO parse_issue"
						+ " (document_iri, line, column, severity, code, message, raw,"
						+ " batch_iri, loading_iri)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		int count = 0;
		try {
			for (IssueMeta meta : issues.values()) {
				String documentIri = resolveDocumentIri(
						meta.issueOwnerIri, meta.sourceIri, documents, documentByDocumentImport);
				if (documentIri == null) {
					continue;
				}
				insert.setString(1, documentIri);
				Location location = meta.location;
				if (location != null && location.isLineColumn()) {
					insert.setInt(2, location.getLine());
					insert.setInt(3, location.getColumn());
				} else {
					insert.setNull(2, Types.INTEGER);
					insert.setNull(3, Types.INTEGER);
				}
				insert.setString(4, meta.severity == null ? "warning" : meta.severity);
				insert.setString(5, meta.code == null ? "" : meta.code);
				insert.setString(6, meta.message == null ? "" : meta.message);
				setNullableString(insert, 7, meta.raw);
				setNullableString(insert, 8, batchIri);
				setNullableString(insert, 9, loadingBySource.get(meta.sourceIri));
				insert.executeUpdate();
				count++;
			}
		} finally {
			insert.close();
		}
		return count;
	}
	
	private static String resolveDocumentIri(
			String issueOwnerIri,
			String sourceIri,
			Map<String, DocumentMeta> documents,
			Map<String, String> documentByDocumentImport
	) {
		if (documents.containsKey(issueOwnerIri)) {
			return issueOwnerIri;
		}
		String byOwner = documentByDocumentImport.get(issueOwnerIri);
		if (byOwner != null) {
			return byOwner;
		}
		if (documents.containsKey(sourceIri)) {
			return sourceIri;
		}
		return documentByDocumentImport.get(sourceIri);
	}
	
	private int insertReportRows(
			List<ConflictRow> reportRows,
			String batchIri,
			Iterable<String> documentIris
	) throws SQLException {
		if (reportRows == null || reportRows.isEmpty()) {
			return 0;
		}
		String documentIri = null;
		int documentCount = 0;
		for (String iri : documentIris) {
			documentIri = iri;
			documentCount++;
		}
		if (documentCount != 1) {
			documentIri = null;
		}
		
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO import_report_row"
						+ " (batch_iri, document_iri, kind, entry_type, field,"
						+ " occurrence_count, first_location)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?)");
		try {
			for (ConflictRow row : reportRows) {
				setNullableString(statement, 1, batchIri);
				setNullableString(statement, 2, documentIri);
				statement.setString(3, row.getKind());
				statement.setString(4, row.getEntryType());
				statement.setString(5, row.getField());
				statement.setInt(6, row.getCount());
				statement.setString(7, row.getFirstLocation());
				statement.executeUpdate();
			}
		} finally {
			statement.close();
		}
		return reportRows.size();
	}
	
	private int replaceVocabulary(Vocabulary vocabulary) throws SQLException {
		if (vocabulary == null) {
			return 0;
		}
		executeWithoutParameters("DELETE FROM vocab_term");
		PreparedStatement statement = connection.prepareStatement(
				"INSERT INTO vocab_term (iri, kind, label, comment, status, defined_in)"
						+ " VALUES (?, ?, ?, ?, ?, ?)");
		try {
			for (Vocabulary.Term term : vocabulary.getTerms()) {
				statement.setString(1, term.getIri());
				setNullableString(statement, 2, term.getKind());
				setNullableString(statement, 3, term.getLabel());
				setNullableString(statement, 4, term.getComment());
				setNullableString(statement, 5, term.getStatus());
				statement.setString(6, join(term.getDefinedIn()));
				statement.executeUpdate();
			}
		} finally {
			statement.close();
		}
		return vocabulary.size();
	}
	
	private void executeWithoutParameters(String sql) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			statement.executeUpdate();
		} finally {
			statement.close();
		}
	}
	
	private static String literalValue(Node node) {
		if (node instanceof Literal) {
			return ((Literal) node).getValue();
		}
		return node.toString();
	}
	
	private static long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
	
	private static String join(List<String> values) {
		StringBuilder out = new StringBuilder();
		for (String value : values) {
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(value);
		}
		return out.toString();
	}
	
	private static void setNullableString(
			PreparedStatement statement,
			int index,
			String value
	) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.VARCHAR);
		} else {
			statement.setString(index, value);
		}
	}
	
	private static final class DocumentMeta {
		final String iri;
		String sourceUrl;
		String sha256;
		long byteLength;
		String format;
		String parserVersion;
		
		DocumentMeta(String iri) {
			this.iri = iri;
		}
	}
	
	private static final class IssueMeta {
		final String issueOwnerIri;
		final String sourceIri;
		final Location location;
		String severity;
		String code;
		String message;
		String raw;
		
		IssueMeta(String issueOwnerIri, String sourceIri, Location location) {
			this.issueOwnerIri = issueOwnerIri;
			this.sourceIri = sourceIri;
			this.location = location;
		}
	}
}
