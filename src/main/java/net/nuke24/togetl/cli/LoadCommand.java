package net.nuke24.togetl.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.nuke24.togetl.db.ConflictRow;
import net.nuke24.togetl.db.LoadResult;
import net.nuke24.togetl.db.LoadedStream;
import net.nuke24.togetl.db.Loader;
import net.nuke24.togetl.db.SQLiteDatabase;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.togetltsv.TOGETLTSVParseException;
import net.nuke24.togetl.togetltsv.TOGETLTSVReader;
import net.nuke24.togetl.vocab.Vocabulary;
import net.nuke24.togetl.vocab.VocabularyLoader;

/**
 * {@code togetl load}: read TOGETLTSV streams into SQLite.
 */
public final class LoadCommand {
	private String dbPath;
	private final List<String> inputs = new ArrayList<String>();
	private final List<String> reportPaths = new ArrayList<String>();
	private final List<String> vocabPaths = new ArrayList<String>();
	private boolean noVocab;
	private boolean simpleProvenance;
	private boolean reset;
	
	private LoadCommand() {
	}
	
	public static int run(String[] args) {
		for (String arg : args) {
			if ("--help".equals(arg) || "-h".equals(arg)) {
				usage(System.out);
				return 0;
			}
		}
		LoadCommand command = new LoadCommand();
		try {
			command.parse(args);
			return command.execute();
		} catch (UsageException e) {
			System.err.println("togetl load: " + e.getMessage());
			usage(System.err);
			return 1;
		} catch (TEFReadException e) {
			System.err.println("togetl load: " + e.getMessage());
			return 1;
		} catch (IOException e) {
			System.err.println("togetl load: " + e.getMessage());
			return 1;
		} catch (SQLException e) {
			System.err.println("togetl load: " + e.getMessage());
			return 1;
		} catch (TOGETLTSVParseException e) {
			System.err.println("togetl load: " + e.getMessage());
			return 1;
		} catch (IllegalArgumentException e) {
			System.err.println("togetl load: " + e.getMessage());
			return 1;
		}
	}
	
	private void parse(String[] args) throws UsageException {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			String key = arg;
			String value = null;
			int eq = arg.indexOf('=');
			if (arg.startsWith("--") && eq > 0) {
				key = arg.substring(0, eq);
				value = arg.substring(eq + 1);
			}
			if ("--db".equals(key)) {
				dbPath = value != null ? value : next(args, ++i, key);
			} else if ("--input".equals(key) || "-i".equals(key)) {
				inputs.add(value != null ? value : next(args, ++i, key));
			} else if ("--report".equals(key)) {
				reportPaths.add(value != null ? value : next(args, ++i, key));
			} else if ("--vocab".equals(key)) {
				vocabPaths.add(value != null ? value : next(args, ++i, key));
			} else if ("--no-vocab".equals(key)) {
				noVocab = true;
			} else if ("--simple-provenance".equals(key)) {
				simpleProvenance = true;
			} else if ("--reset".equals(key)) {
				reset = true;
			} else {
				throw new UsageException("Unknown option: " + arg);
			}
		}
		if (dbPath == null || dbPath.isEmpty()) {
			throw new UsageException("--db is required");
		}
		if (inputs.isEmpty()) {
			throw new UsageException("At least one --input is required");
		}
	}
	
	private static String next(String[] args, int index, String option) throws UsageException {
		if (index >= args.length) {
			throw new UsageException("Missing value for " + option);
		}
		return args[index];
	}
	
	private int execute() throws IOException, SQLException, TOGETLTSVParseException, TEFReadException {
		List<LoadedStream> streams = new ArrayList<LoadedStream>();
		for (String input : inputs) {
			Path path = Paths.get(input);
			byte[] bytes = Files.readAllBytes(path);
			Reader reader = new InputStreamReader(
					new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
			List<Statement> statements;
			try {
				statements = TOGETLTSVReader.readAll(reader);
			} finally {
				reader.close();
			}
			streams.add(new LoadedStream(
					path.toString(), Identifiers.sha256(bytes), statements));
		}
		
		List<ConflictRow> reportRows = new ArrayList<ConflictRow>();
		for (String reportPath : reportPaths) {
			reportRows.addAll(readReport(Paths.get(reportPath)));
		}
		
		Vocabulary vocabulary = null;
		if (!noVocab) {
			vocabulary = new Vocabulary();
			if (vocabPaths.isEmpty()) {
				for (Path path : VocabularyLoader.defaultVocabFiles()) {
					VocabularyLoader.loadInto(vocabulary, path);
				}
			} else {
				for (String vocabPath : vocabPaths) {
					VocabularyLoader.loadInto(vocabulary, Paths.get(vocabPath));
				}
			}
		}
		
		Connection connection = SQLiteDatabase.open(Paths.get(dbPath));
		try {
			SQLiteDatabase.initialize(connection);
			connection.setAutoCommit(false);
			try {
				if (reset) {
					SQLiteDatabase.reset(connection);
				}
				Loader loader = new Loader(connection, simpleProvenance);
				LoadResult result = loader.load(streams, reportRows, vocabulary);
				connection.commit();
				printResult(result);
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			} catch (RuntimeException e) {
				connection.rollback();
				throw e;
			}
		} finally {
			connection.close();
		}
		return 0;
	}
	
	private void printResult(LoadResult result) {
		System.out.println("Loaded " + result.getDocumentIris().size()
				+ " document(s), " + result.getStatementCount() + " statement(s)");
		if (result.getBatchIri() != null) {
			System.out.println("  batch " + result.getBatchIri()
					+ (result.isReplacedBatch() ? " (replaced)" : ""));
		} else {
			System.out.println("  provenance: document only");
		}
		System.out.println("  parse issues " + result.getParseIssueCount()
				+ ", report rows " + result.getReportRowCount()
				+ ", vocab terms " + result.getVocabTermCount());
	}
	
	private static List<ConflictRow> readReport(Path path)
			throws IOException, TOGETLTSVParseException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		List<ConflictRow> rows = new ArrayList<ConflictRow>();
		int lineNumber = 0;
		for (String line : lines) {
			lineNumber++;
			if (line.trim().isEmpty()) {
				continue;
			}
			String[] columns = line.split("\t", -1);
			if (columns.length < 4) {
				throw new TOGETLTSVParseException(
						lineNumber, "expected at least 4 report columns");
			}
			if ("kind".equals(columns[0])) {
				continue;
			}
			int count;
			try {
				count = Integer.parseInt(columns[3]);
			} catch (NumberFormatException e) {
				throw new TOGETLTSVParseException(
						lineNumber, "invalid report count: " + columns[3]);
			}
			rows.add(new ConflictRow(
					columns[0],
					columns[1],
					columns[2],
					count,
					columns.length > 4 ? columns[4] : ""));
		}
		return rows;
	}
	
	public static void usage(java.io.PrintStream err) {
		err.println("Usage: togetl load --db <file> --input <file> [options]");
		err.println();
		err.println("  --db <file>            SQLite database (created if missing)");
		err.println("  --input <file>         TOGETLTSV stream (repeatable, required)");
		err.println("  --report <file>        Importer report TSV to load (repeatable)");
		err.println("  --vocab <file>         Vocabulary TEF file (default: all vocab/*.tef)");
		err.println("  --no-vocab             Do not load vocabulary terms");
		err.println("  --simple-provenance    Record only the source document");
		err.println("  --reset                Delete existing rows before loading");
		err.println();
		err.println("The same inputs and options produce the same batch IRI, so");
		err.println("re-running a load replaces that batch instead of duplicating it.");
	}
	
	private static final class UsageException extends Exception {
		private static final long serialVersionUID = 1L;
		
		UsageException(String message) {
			super(message);
		}
	}
}
