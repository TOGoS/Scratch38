package net.nuke24.togetl.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;

import net.nuke24.togetl.Version;
import net.nuke24.togetl.dialect.Dialects;
import net.nuke24.togetl.dialect.TEFDialect;
import net.nuke24.togetl.dialect.TEFDocumentSink;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.hash.BitprintScheme;
import net.nuke24.togetl.etl.SourceProvenance;
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
	private String sourceUri;
	private String deriveKind;
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
			} else if ("--source-uri".equals(key)) {
				sourceUri = value != null ? value : next(args, ++i, key);
			} else if ("--derive-source-uri".equals(key)) {
				deriveKind = value != null ? value : next(args, ++i, key);
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
		if (sourceUri == null && deriveKind == null) {
			throw new UsageException("--source-uri or --derive-source-uri is required");
		}
		if (sourceUri != null && deriveKind != null) {
			throw new UsageException("--source-uri and --derive-source-uri are mutually exclusive");
		}
		// Note: If ever there is more than just 'bitprint', we might consider
		// centralizing information about digests somewhere.
		if (deriveKind != null && !"bitprint".equals(deriveKind)) {
			throw new UsageException("Unknown --derive-source-uri kind: " + deriveKind);
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
		boolean stdin = "-".equals(input);
		Path file = stdin ? spoolStdin() : Paths.get(input);
		try {
			importFile(selectedDialect, file);
		} finally {
			if (stdin) {
				Files.deleteIfExists(file);
			}
		}
	}
	
	private void importFile(TEFDialect selectedDialect, Path file) throws IOException, TEFReadException {
		SourceProvenance provenance = digest(file);
		String effectiveSourceUri;
		if (deriveKind != null) {
			effectiveSourceUri = provenance.getBitprintUri();
		} else {
			effectiveSourceUri = sourceUri;
			if (effectiveSourceUri.startsWith(BitprintScheme.BITPRINTURNPREFIX)) {
				if (!effectiveSourceUri.equals(provenance.getBitprintUri())) {
					throw new IOException("source URI does not match the input's bitprint: expected "
							+ provenance.getBitprintUri() + " but got " + effectiveSourceUri);
				}
			} else {
				System.err.println("togetl import: warning: source URI is not a bitprint URN ("
						+ effectiveSourceUri + "); this may cause problems downstream");
			}
		}
		
		SourceDocument source = SourceDocument.of(effectiveSourceUri, "tef", Version.TEF_PARSER_VERSION);
		ImportReport report = new ImportReport();
		CountingConsumer counter = new CountingConsumer();
		boolean fileOutput = !"-".equals(output);
		boolean complete = false;
		Writer outputWriter = openOutput(output);
		try {
			TOGETLTSVWriter tsv = new TOGETLTSVWriter(outputWriter, AliasTable.withDefaults());
			StatementConsumer consumer = new StatementConsumer() {
				@Override
				public void accept(Statement statement) throws IOException {
					tsv.write(statement);
					counter.accept(statement);
				}
			};
			TEFDocumentSink document = selectedDialect.begin(source, consumer, report);
			CountingSink entries = new CountingSink(document);
			InputStream in = Files.newInputStream(file);
			try {
				TEFReader.read(effectiveSourceUri, in, entries);
			} finally {
				in.close();
			}
			document.finish(provenance);
			tsv.flush();
			complete = true;
			
			if (reportPath != null) {
				writeReport(reportPath, report);
			}
			System.err.println("Imported " + effectiveSourceUri);
			System.err.println("  document " + source.getDocumentIri());
			System.err.println("  document-import " + source.getDocumentImportIri());
			System.err.println("  entries " + entries.entryCount + ", statements " + counter.count);
			System.err.println("  unmapped " + report.getOccurrenceCount()
					+ " occurrence(s) in " + report.getRowCount() + " row(s)");
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
	}
	
	private static Path spoolStdin() throws IOException {
		Path spool = Files.createTempFile("togetl-import-", ".tef");
		try {
			Files.copy(System.in, spool, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Files.deleteIfExists(spool);
			throw e;
		}
		return spool;
	}
	
	private static SourceProvenance digest(Path file) throws IOException {
		SourceProvenance.Digester digester = new SourceProvenance.Digester();
		InputStream in = Files.newInputStream(file);
		try {
			byte[] block = new byte[65536];
			int n;
			while ((n = in.read(block)) >= 0) {
				if (n > 0) {
					digester.accept(Optional.of(Arrays.copyOf(block, n)));
				}
			}
		} finally {
			in.close();
		}
		digester.accept(Optional.<byte[]>empty());
		return digester.result();
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
		err.println("  --source-uri <uri>     Source URI of the document");
		err.println("  --derive-source-uri <kind>  Derive the source URI from the content (bitprint)");
		err.println("  --dialect <name>       " + Dialects.names() + " (default: generic)");
		err.println("  --report <file>        Write the unmapped-construct report as TSV");
	}
	
	private static final class CountingSink implements TEFSink {
		private final TEFDocumentSink delegate;
		long entryCount;
		
		CountingSink(TEFDocumentSink delegate) {
			this.delegate = delegate;
		}
		
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
