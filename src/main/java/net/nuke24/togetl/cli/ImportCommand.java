package net.nuke24.togetl.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.nuke24.togetl.Version;
import net.nuke24.togetl.dialect.Dialects;
import net.nuke24.togetl.dialect.TEFDialect;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFComment;
import net.nuke24.togetl.tef.TEFDirective;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.tef.TEFReader;
import net.nuke24.togetl.tef.TEFSink;
import net.nuke24.togetl.togetltsv.AliasTable;
import net.nuke24.togetl.togetltsv.TOGETLTSVWriter;

/**
 * {@code togetl import}: read one source document and write a TOGETLTSV stream.
 */
public final class ImportCommand {
	private String input;
	private String output = "-";
	private String sourceUrl;
	private String dialect = "generic";
	private String reportPath;
	
	private ImportCommand() {
	}
	
	public static int run(String[] args) {
		for (String arg : args) {
			if ("--help".equals(arg) || "-h".equals(arg)) {
				usage(System.out);
				return 0;
			}
		}
		ImportCommand command = new ImportCommand();
		try {
			command.parse(args);
			command.execute();
			return 0;
		} catch (UsageException e) {
			System.err.println("togetl import: " + e.getMessage());
			usage(System.err);
			return 1;
		} catch (TEFReadException e) {
			System.err.println("togetl import: " + e.getMessage());
			return 1;
		} catch (IOException e) {
			System.err.println("togetl import: " + e.getMessage());
			return 1;
		} catch (IllegalArgumentException e) {
			System.err.println("togetl import: " + e.getMessage());
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
			if ("--input".equals(key) || "-i".equals(key)) {
				input = value != null ? value : next(args, ++i, key);
			} else if ("--output".equals(key) || "-o".equals(key)) {
				output = value != null ? value : next(args, ++i, key);
			} else if ("--source-url".equals(key)) {
				sourceUrl = value != null ? value : next(args, ++i, key);
			} else if ("--dialect".equals(key)) {
				dialect = value != null ? value : next(args, ++i, key);
			} else if ("--report".equals(key)) {
				reportPath = value != null ? value : next(args, ++i, key);
			} else {
				throw new UsageException("Unknown option: " + arg);
			}
		}
		if (input == null || input.isEmpty()) {
			throw new UsageException("--input is required");
		}
		if (output == null || output.isEmpty()) {
			throw new UsageException("--output must not be empty");
		}
	}
	
	private static String next(String[] args, int index, String option) throws UsageException {
		if (index >= args.length) {
			throw new UsageException("Missing value for " + option);
		}
		return args[index];
	}
	
	private void execute() throws IOException, TEFReadException {
		TEFDialect selectedDialect = Dialects.byName(dialect);
		
		if ("-".equals(input)) {
			Path spool = Files.createTempFile("togetl-import-", ".tef");
			try {
				Files.copy(System.in, spool, StandardCopyOption.REPLACE_EXISTING);
				executeFile(selectedDialect, spool);
			} finally {
				Files.deleteIfExists(spool);
			}
		} else {
			executeFile(selectedDialect, Paths.get(input));
		}
	}
	
	private void executeFile(TEFDialect selectedDialect, Path file) throws IOException, TEFReadException {
		String effectiveSourceUrl = sourceUrl != null ? sourceUrl : defaultSourceUrl(input);
		Provenance provenance = Provenance.scan(file);
		SourceDocument source = SourceDocument.of(
				effectiveSourceUrl,
				provenance.sha256,
				provenance.byteLength,
				"tef",
				Version.TEF_PARSER_VERSION);
		
		ImportReport report = new ImportReport();
		CountingSink sink = new CountingSink();
		CountingConsumer counter = new CountingConsumer();
		boolean fileOutput = !"-".equals(output);
		boolean complete = false;
		Writer outputWriter = openOutput(output);
		try {
			final TOGETLTSVWriter tsv = new TOGETLTSVWriter(outputWriter, AliasTable.withDefaults());
			StatementConsumer consumer = new StatementConsumer() {
				@Override
				public void accept(Statement statement) throws IOException {
					tsv.write(statement);
					counter.accept(statement);
				}
			};
			sink.delegate = selectedDialect.begin(source, provenance.bom, consumer, report);
			InputStream in = Files.newInputStream(file);
			try {
				TEFReader.read(effectiveSourceUrl, in, sink);
			} finally {
				in.close();
			}
			tsv.flush();
			complete = true;
		} finally {
			if (fileOutput) {
				outputWriter.close();
				if (!complete) {
					Files.deleteIfExists(Paths.get(output));
				}
			} else {
				outputWriter.flush();
			}
		}
		
		if (reportPath != null) {
			writeReport(reportPath, report);
		}
		
		System.err.println("Imported " + effectiveSourceUrl);
		System.err.println("  document " + source.getDocumentIri());
		System.err.println("  document-import " + source.getDocumentImportIri());
		System.err.println("  entries " + sink.entryCount + ", statements " + counter.count);
		System.err.println("  unmapped " + report.getOccurrenceCount()
				+ " occurrence(s) in " + report.getRowCount() + " row(s)");
	}
	
	private static String defaultSourceUrl(String input) throws IOException {
		if ("-".equals(input)) {
			throw new IllegalArgumentException(
					"--source-url is required when reading from standard input");
		}
		Path path = Paths.get(input).toAbsolutePath().normalize();
		return path.toUri().toString();
	}
	
	private static Writer openOutput(String path) throws IOException {
		if ("-".equals(path)) {
			return new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
		}
		Path target = Paths.get(path);
		Path parent = target.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		OutputStream out = Files.newOutputStream(target);
		return new OutputStreamWriter(out, StandardCharsets.UTF_8);
	}
	
	private static void writeReport(String path, ImportReport report) throws IOException {
		Path target = Paths.get(path);
		Path parent = target.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Writer out = new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8);
		try {
			report.writeTsv(out);
		} finally {
			out.close();
		}
	}
	
	public static void usage(java.io.PrintStream err) {
		err.println("Usage: togetl import --input <file|-> [options]");
		err.println();
		err.println("  --input <file|->       Source document (required)");
		err.println("  --output <file|->      TOGETLTSV output (default: -)");
		err.println("  --source-url <url>     Canonical source URL (default: file: URI)");
		err.println("  --dialect <name>       " + Dialects.names() + " (default: generic)");
		err.println("  --report <file>        Write the unmapped-construct report as TSV");
	}
	
	/** Digest, length, and BOM of the input, found in one pass so metadata can come first. */
	private static final class Provenance {
		final String sha256;
		final long byteLength;
		final boolean bom;
		
		private Provenance(String sha256, long byteLength, boolean bom) {
			this.sha256 = sha256;
			this.byteLength = byteLength;
			this.bom = bom;
		}
		
		static Provenance scan(Path file) throws IOException {
			MessageDigest digest;
			try {
				digest = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 is unavailable", e);
			}
			byte[] head = new byte[3];
			int headLength = 0;
			long length = 0;
			byte[] buffer = new byte[65536];
			InputStream in = new DigestInputStream(Files.newInputStream(file), digest);
			try {
				int n;
				while ((n = in.read(buffer)) >= 0) {
					for (int i = 0; i < n && headLength < head.length; i++) {
						head[headLength++] = buffer[i];
					}
					length += n;
				}
			} finally {
				in.close();
			}
			boolean bom = headLength == 3
					&& head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF;
			return new Provenance(Identifiers.hex(digest.digest()), length, bom);
		}
	}
	
	private static final class CountingSink implements TEFSink {
		TEFSink delegate;
		long entryCount;
		
		@Override
		public void documentComment(TEFComment comment) throws IOException {
			delegate.documentComment(comment);
		}
		
		@Override
		public void documentDirective(TEFDirective directive) throws IOException {
			delegate.documentDirective(directive);
		}
		
		@Override
		public void entry(TEFEntry entry) throws IOException {
			entryCount++;
			delegate.entry(entry);
		}
	}
	
	private static final class CountingConsumer implements StatementConsumer {
		int count;
		
		@Override
		public void accept(Statement statement) {
			count++;
		}
	}
	
	private static final class UsageException extends Exception {
		private static final long serialVersionUID = 1L;
		
		UsageException(String message) {
			super(message);
		}
	}
}
