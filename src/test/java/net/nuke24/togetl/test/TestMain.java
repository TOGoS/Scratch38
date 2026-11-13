package net.nuke24.togetl.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.nuke24.togetl.cli.ImportCommand;
import net.nuke24.togetl.cli.LoadCommand;
import net.nuke24.togetl.cli.VocabCommand;
import net.nuke24.togetl.db.SQLiteDatabase;
import net.nuke24.togetl.dialect.GenericTEFDialect;
import net.nuke24.togetl.dialect.TEFDialect;
import net.nuke24.togetl.dialect.TEFDocumentSink;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.etl.SourceProvenance;
import net.nuke24.togetl.hash.Base32;
import net.nuke24.togetl.hash.BitprintDigest;
import net.nuke24.togetl.hash.BitprintScheme;
import net.nuke24.togetl.hash.Tiger;
import net.nuke24.togetl.hash.TigerTree;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.mapping.MappingTable;
import net.nuke24.togetl.mapping.MappingTEFDialect;
import net.nuke24.togetl.org.OrgDocument;
import net.nuke24.togetl.org.OrgHeading;
import net.nuke24.togetl.org.OrgParser;
import net.nuke24.togetl.org.OrgReader;
import net.nuke24.togetl.org.OrgSyntaxException;
import net.nuke24.togetl.org.OrgTable;
import net.nuke24.togetl.org.OrgTableRow;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFComment;
import net.nuke24.togetl.tef.TEFDirective;
import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.tef.TEFReader;
import net.nuke24.togetl.tef.TEFSink;
import net.nuke24.togetl.togetltsv.AliasTable;
import net.nuke24.togetl.togetltsv.TOGETLTSVReader;
import net.nuke24.togetl.togetltsv.TOGETLTSVWriter;
import net.nuke24.togetl.util.MultiSink;
import net.nuke24.togetl.util.Sink;
import net.nuke24.togetl.util.PercentCoder;
import net.nuke24.togetl.vocab.Vocab;
import net.nuke24.togetl.vocab.Vocabulary;
import net.nuke24.togetl.vocab.VocabularyLoader;
import net.nuke24.togetl.vocab.VocabularyReport;

/**
 * Minimal test runner. Keeps the build dependency-free.
 */
public final class TestMain {
	private static final String DOC = "tag:nuke24.net,2026-11:togetl:document:abc";
	private static final String PART = "tag:nuke24.net,2026-11:togetl:part:p1310";
	private static final String TOGETL = "tag:nuke24.net,2026-11:togetl:";
	private static final String GENERAL = "tag:nuke24.net,2026-11:general:";
	private static final String PARTS3D = "tag:nuke24.net,2026-11:3dparts:";
	private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	private static final String XSD = "http://www.w3.org/2001/XMLSchema#";
	
	private static final String DB_DOC = TOGETL + "document:testdoc";
	private static final String DB_IMPORT = TOGETL + "document-import:testimport";
	private static final String DB_PART = PARTS3D + "part:p1";
	private static final String DB_STL_FILE =
			"http://wherever-files.nuke24.net/uri-res/raw/urn:bitprint:ABC/p1.stl";
			
	private static int failures = 0;
	private static int passed = 0;
	private static int skipped = 0;
	/**
	 * Categories that run. "core" is always on; the others are opt-in with
	 * --include=file-tests,memory-tests, because they touch the file system or
	 * take a while.
	 */
	private static final Set<String> enabledCategories = new HashSet<String>(
			Collections.singletonList("core"));
			
	private interface Check {
		void run() throws Exception;
	}
	
	private TestMain() {
	}
	
	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("--include=")) {
				for (String category : arg.substring("--include=".length()).split(",")) {
					enabledCategories.add(category.trim());
				}
			} else {
				System.err.println("Usage: TestMain [--include=file-tests,memory-tests]");
				System.exit(2);
			}
		}
		run("percent coder", TestMain::testPercentCoder);
		run("locations", TestMain::testLocations);
		run("aliases", TestMain::testAliases);
		run("writer/reader round trip", TestMain::testRoundTrip);
		run("stream concatenation", TestMain::testConcatenation);
		run("deterministic output", TestMain::testDeterminism);
		run("hand-written stream", TestMain::testHandWrittenStream);
		run("tef basic entry", TestMain::testTefBasicEntry);
		run("tef headers and continuations", TestMain::testTefHeadersAndContinuations);
		run("tef content boundaries", TestMain::testTefContentBoundaries);
		run("tef equals escape", TestMain::testTefEqualsEscape);
		run("tef escaped header equals", TestMain::testTefEscapedHeaderEquals);
		run("tef comments and directives", TestMain::testTefCommentsAndDirectives);
		run("tef orphan continuation", TestMain::testTefOrphanContinuation);
		run("tef header without colon", TestMain::testTefHeaderWithoutColon);
		run("tef tight header", TestMain::testTefTightHeader);
		run("tef byte order mark", TestMain::testTefByteOrderMark);
		run("tef invalid utf-8", TestMain::testTefInvalidUtf8);
		run("tef spans", TestMain::testTefSpans);
		run("tef crlf", TestMain::testTefCrLf);
		run("tef streamed input", TestMain::testTefStreamedInput);
		run("tef utf-8 split across blocks", TestMain::testTefUtf8SplitAcrossBlocks);
		run("tef invalid utf-8 split across blocks", TestMain::testTefInvalidUtf8SplitAcrossBlocks);
		run("tef entries arrive before end of input", TestMain::testTefEntriesArriveBeforeEnd);
		run("source provenance", TestMain::testSourceProvenance);
		run("multi sink order", TestMain::testMultiSinkOrder);
		run("identifiers", TestMain::testIdentifiers);
		run("hash known answers", TestMain::testHashKnownAnswers);
		run("generic dialect", TestMain::testGenericDialect);
		run("generic dialect round trip", TestMain::testGenericDialectRoundTrip);
		run("parts mapping", TestMain::testPartsMapping);
		run("parts conflicts and duplicates", TestMain::testPartsConflicts);
		run("namespaces mapping", TestMain::testNamespacesMapping);
		run("vocabulary report", TestMain::testVocabularyReport);
		run("vocab command", TestMain::testVocabCommand);
		run("org reader", TestMain::testOrgReader);
		run("org heading metadata", TestMain::testOrgHeadingMetadata);
		run("org directives and tables", TestMain::testOrgDirectivesAndTables);
		run("org recovery", TestMain::testOrgRecovery);
		run("org line endings and cells", TestMain::testOrgLineEndingsAndCells);
		run("org chunked input", TestMain::testOrgChunkedInput);
		run("org keywords and duplicates", TestMain::testOrgKeywordsAndDuplicates);
		run("org table separator and input rules", TestMain::testOrgTableSeparatorAndInputRules);
		run("import command", TestMain::testImportCommand);
		run("database load", TestMain::testDatabaseLoad);
		run("database simple provenance", TestMain::testDatabaseSimpleProvenance);
		
		run("file-tests", "import leaves no output on syntax error", TestMain::testImportNoOutputOnError);
		run("file-tests", "import rejects --tolerance", TestMain::testImportRejectsTolerance);
		run("file-tests", "import writes provenance", TestMain::testImportWritesProvenance);
		run("file-tests", "import from stdin", TestMain::testImportFromStdin);
		run("file-tests", "import derives bitprint source", TestMain::testImportDerivesBitprint);
		run("file-tests", "import rejects mismatched bitprint", TestMain::testImportRejectsMismatchedBitprint);
		run("file-tests", "import rejects bad source options", TestMain::testImportRejectsBadSourceOptions);
		
		run("memory-tests", "import streams in a small heap", TestMain::testImportStreamsInSmallHeap);
		
		System.out.println();
		if (failures > 0) {
			System.out.println(failures + " test(s) failed, " + passed + " passed, " + skipped + " skipped");
			System.exit(1);
		}
		System.out.println(passed + " test(s) passed, " + skipped + " skipped");
	}
	
	private static void run(String name, Check check) {
		run("core", name, check);
	}
	
	private static void run(String category, String name, Check check) {
		if (!enabledCategories.contains(category)) {
			skipped++;
			System.out.println("skip " + name + " (needs --include=" + category + ")");
			return;
		}
		try {
			check.run();
			passed++;
			System.out.println("ok   " + name);
		} catch (Throwable t) {
			failures++;
			System.out.println("FAIL " + name);
			t.printStackTrace(System.out);
		}
	}
	
	private static void testPercentCoder() {
		assertEquals("Bob%20Hope", PercentCoder.encode("Bob Hope"), "space encoding");
		assertEquals("a%2Cb%7Cc%25d%23e", PercentCoder.encode("a,b|c%d#e"), "reserved characters");
		String value = "line1\nline2 \u00e9 \ud83d\ude00";
		assertEquals(value, PercentCoder.decode(PercentCoder.encode(value)), "unicode round trip");
		assertEquals("data:,Bob%20Hope", Literal.of("Bob Hope").getDataUri(), "literal data URI");
	}
	
	private static void testLocations() {
		assertEquals("L1C1", Location.at(1, 1).toString(), "one-based location");
		assertEquals("L1C1...L2C3", Location.span(1, 1, 2, 3).toString(), "line span");
		assertEquals("-", Location.none().toString(), "none location");
		assertEquals(Location.at(1, 1), Location.parse("l0c0"), "zero-based parse");
		assertEquals(Location.at(2, 3), Location.parse("L2C3"), "one-based parse");
		assertEquals("b42...b99", Location.byteSpan(42, 99).toString(), "byte span");
		assertEquals(Location.byteSpan(42, 99), Location.parse("b42...b99"), "byte span parse");
	}
	
	private static void testAliases() {
		AliasTable aliases = AliasTable.withDefaults();
		assertEquals(RDF + "type", aliases.expand("rdf:type"), "expand default alias");
		assertEquals("rdf:type", aliases.compress(RDF + "type"), "compress default alias");
		assertEquals(RDF, aliases.expand("rdf:"), "expand namespace alias");
		
		aliases.define("title", aliases.expand("dcterms:title"));
		assertEquals("http://purl.org/dc/terms/title", aliases.expand("title"), "alias in terms of alias");
		assertEquals("title", aliases.compress("http://purl.org/dc/terms/title"), "exact alias compression");
		
		aliases.define("ex:", "http://example.com/");
		aliases.define("ex-sub:", "http://example.com/sub/");
		assertEquals("http://example.com/sub/x", aliases.expand("ex-sub:x"), "longest alias wins");
		assertEquals("ex-sub:x", aliases.compress("http://example.com/sub/x"), "longest target wins");
	}
	
	private static void testRoundTrip() throws Exception {
		Statement type = new Statement(
				DOC, Location.at(12, 1), IRI.of(PART), IRI.of(RDF + "type"), IRI.of(TOGETL + "Part"));
		Statement name = new Statement(
				DOC, Location.at(13, 1), IRI.of(PART), IRI.of(TOGETL + "name"), Literal.of("Bob Hope"));
		Statement count = new Statement(
				DOC, Location.at(14, 1), IRI.of(PART), IRI.of(TOGETL + "count"),
				Literal.of("42", IRI.of(XSD + "integer")));
		Statement note = new Statement(
				DOC, Location.at(15, 1), IRI.of(PART), IRI.of(TOGETL + "note"), BlankNode.of("h1"));
				
		String text = write(type, name, count, note);
		check(text.contains("data:,Bob%20Hope"), "literal is percent-encoded");
		check(text.contains("data:,42|xsd:integer"), "datatype is compressed");
		
		List<Statement> read = TOGETLTSVReader.readAll(new StringReader(text));
		assertEquals(4, read.size(), "statement count");
		assertEquals(type, read.get(0), "statement 0");
		assertEquals(name, read.get(1), "statement 1");
		assertEquals(count, read.get(2), "statement 2");
		assertEquals(note, read.get(3), "statement 3");
	}
	
	private static void testConcatenation() throws Exception {
		Statement a = new Statement(
				DOC, Location.at(1, 1), IRI.of(PART), IRI.of(TOGETL + "name"), Literal.of("A"));
		Statement b = new Statement(
				DOC, Location.at(2, 1), IRI.of(PART), IRI.of(TOGETL + "name"), Literal.of("B"));
		String first = write(a);
		String second = write(b);
		
		List<Statement> read = TOGETLTSVReader.readAll(new StringReader(first + second));
		assertEquals(2, read.size(), "concatenated statement count");
		assertEquals(a, read.get(0), "first concatenated statement");
		assertEquals(b, read.get(1), "second concatenated statement");
	}
	
	private static void testDeterminism() throws Exception {
		Statement statement = new Statement(
				DOC, Location.at(1, 1), IRI.of(PART), IRI.of(TOGETL + "name"), Literal.of("A"));
		assertEquals(write(statement), write(statement), "identical input produces identical output");
	}
	
	private static void testHandWrittenStream() throws Exception {
		String text =
				"#format urn:uuid:b783bac7-58e9-4340-93ef-7973914732d5\n"
				+ "# a comment\n"
				+ "#alias doc tag:nuke24.net,2026-11:togetl:document:abc\n"
				+ "#alias togetl: tag:nuke24.net,2026-11:togetl:\n"
				+ "#alias rdf: http://www.w3.org/1999/02/22-rdf-syntax-ns#\n"
				+ "#alias name togetl:name\n"
				+ "\n"
				+ "doc l0c13 doc rdf:type togetl:Part\n"
				+ "doc L1C1 doc name data:,Bob%20Hope\n";
				
		List<Statement> read = TOGETLTSVReader.readAll(new StringReader(text));
		assertEquals(2, read.size(), "hand-written statement count");
		assertEquals(
				new Statement(
						DOC, Location.at(1, 14), IRI.of(DOC), IRI.of(RDF + "type"), IRI.of(TOGETL + "Part")),
				read.get(0),
				"hand-written type statement");
		assertEquals(
				new Statement(
						DOC, Location.at(1, 1), IRI.of(DOC), IRI.of(TOGETL + "name"), Literal.of("Bob Hope")),
				read.get(1),
				"hand-written name statement");
	}
	
	private static void testTefBasicEntry() throws Exception {
		TEFDocument document = readTef("=part p1310\nname: Widget\n\nSome content\n");
		assertEquals(1, document.getEntries().size(), "entry count");
		
		TEFEntry entry = document.getEntries().get(0);
		assertEquals("part", entry.getType(), "entry type");
		assertEquals("p1310", entry.getValue(), "entry value");
		assertEquals(1, entry.getHeaders().size(), "header count");
		assertEquals("name", entry.getHeaders().get(0).getKey(), "header key");
		assertEquals("Widget", entry.getHeaders().get(0).getValue(), "header value");
		assertEquals("Some content\n", entry.getContent(), "content");
	}
	
	private static void testTefHeadersAndContinuations() throws Exception {
		String text =
				"=foo bar baz\r\n"
				+ "hi:there: foo bar\r\n"
				+ "\tbaz quux\n"
				+ "bill: ted\r\n"
				+ "empty:\n"
				+ "empty: \n"
				+ "kyanu:\n"
				+ "\thorsey\n"
				+ "kevin: \n"
				+ "\t";
				
		TEFDocument document = readTef(text);
		assertEquals(1, document.getEntries().size(), "entry count");
		
		TEFEntry entry = document.getEntries().get(0);
		assertEquals("foo", entry.getType(), "entry type");
		assertEquals("bar baz", entry.getValue(), "entry value");
		assertEquals(6, entry.getHeaders().size(), "header count");
		assertEquals("hi:there", entry.getHeaders().get(0).getKey(), "colon in key");
		assertEquals("foo bar\r\nbaz quux", entry.getHeaders().get(0).getValue(), "crlf continuation");
		assertEquals("bill", entry.getHeaders().get(1).getKey(), "second key");
		assertEquals("ted", entry.getHeaders().get(1).getValue(), "second value");
		assertEquals("empty", entry.getHeaders().get(2).getKey(), "bare key");
		assertEquals("", entry.getHeaders().get(2).getValue(), "bare key value");
		assertEquals("empty", entry.getHeaders().get(3).getKey(), "second bare key");
		assertEquals("", entry.getHeaders().get(3).getValue(), "second bare key value");
		assertEquals("kyanu", entry.getHeaders().get(4).getKey(), "continuation-only header");
		assertEquals("\nhorsey", entry.getHeaders().get(4).getValue(), "lf continuation");
		assertEquals("kevin", entry.getHeaders().get(5).getKey(), "empty continuation header");
		assertEquals("\n", entry.getHeaders().get(5).getValue(), "empty continuation");
		assertEquals("", entry.getContent(), "content");
	}
	
	private static void testTefContentBoundaries() throws Exception {
		TEFDocument document = readTef("=foo bar\n\nhello\n=foo baz\n\nworld");
		assertEquals(2, document.getEntries().size(), "entry count");
		assertEquals("hello", document.getEntries().get(0).getContent(), "first content");
		assertEquals("world", document.getEntries().get(1).getContent(), "second content");
	}
	
	private static void testTefEqualsEscape() throws Exception {
		TEFDocument document = readTef("=foo bar\n\nhello\n==\n");
		assertEquals(1, document.getEntries().size(), "entry count");
		assertEquals("hello\n=\n", document.getEntries().get(0).getContent(), "escaped equals");
	}
	
	private static void testTefEscapedHeaderEquals() throws Exception {
		TEFDocument document = readTef("=foo bar\n==note: value\n\ncontent");
		assertEquals(1, document.getEntries().size(), "entry count");
		
		TEFEntry entry = document.getEntries().get(0);
		assertEquals(1, entry.getHeaders().size(), "header count");
		assertEquals("=note", entry.getHeaders().get(0).getKey(), "escaped header key");
		assertEquals("value", entry.getHeaders().get(0).getValue(), "escaped header value");
	}
	
	private static void testTefCommentsAndDirectives() throws Exception {
		String text =
				"#format x\n"
				+ "# a comment\n"
				+ "=part p1\n"
				+ "# inner comment\n"
				+ "name: Bob\n"
				+ "\n"
				+ "content";
				
		TEFDocument document = readTef(text);
		assertEquals(1, document.getDirectives().size(), "document directive count");
		assertEquals("format", document.getDirectives().get(0).getName(), "directive name");
		assertEquals("x", document.getDirectives().get(0).getValue(), "directive value");
		assertEquals(1, document.getComments().size(), "document comment count");
		assertEquals("# a comment", document.getComments().get(0).getText(), "document comment");
		
		TEFEntry entry = document.getEntries().get(0);
		assertEquals(1, entry.getComments().size(), "entry comment count");
		assertEquals("# inner comment", entry.getComments().get(0).getText(), "entry comment");
		assertEquals("Bob", entry.getHeaders().get(0).getValue(), "header value");
		assertEquals("content", entry.getContent(), "content");
	}
	
	private static void testTefOrphanContinuation() throws Exception {
		checkTefError("=part p1\n name: Bob\n", "Unexpected indented line");
	}
	
	private static void testTefHeaderWithoutColon() throws Exception {
		checkTefError(
				"=part p1\nname: Bob\nThis is content.\n\nmore",
				"Header line has no 'key: value' colon");
	}
	
	private static void testTefTightHeader() throws Exception {
		checkTefError(
				"=recording x\nx-rdf-subject:urn:bitprint:ABC\ntitle: Bob\n",
				"Missing space after header colon");
	}
	
	private static void testTefByteOrderMark() throws Exception {
		byte[] body = "=part p1\n".getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[body.length + 3];
		bytes[0] = (byte) 0xEF;
		bytes[1] = (byte) 0xBB;
		bytes[2] = (byte) 0xBF;
		System.arraycopy(body, 0, bytes, 3, body.length);
		
		TEFDocument document = TEFReader.read(DOC, bytes);
		check(document.hasBom(), "BOM should be detected");
		assertEquals((long) bytes.length, document.getByteLength(), "byte length");
		assertEquals(1, document.getEntries().size(), "entry count");
		assertEquals("part", document.getEntries().get(0).getType(), "entry type");
		assertEquals("p1", document.getEntries().get(0).getValue(), "entry value");
	}
	
	private static void testTefInvalidUtf8() throws Exception {
		byte[] prefix = "=part p1\nname: ".getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[prefix.length + 2];
		System.arraycopy(prefix, 0, bytes, 0, prefix.length);
		bytes[prefix.length] = (byte) 0xFF;
		bytes[prefix.length + 1] = (byte) '\n';
		checkTefError(bytes, "Invalid UTF-8");
		
		byte[] truncated = new byte[prefix.length + 1];
		System.arraycopy(prefix, 0, truncated, 0, prefix.length);
		truncated[prefix.length] = (byte) 0xC3;
		checkTefError(truncated, "Invalid UTF-8");
	}
	
	private static void testTefStreamedInput() throws Exception {
		StringBuilder text = new StringBuilder("=part p1\nname: Bob\n\n");
		for (int i = 0; i < 20000; i++) {
			text.append("caf\u00e9 line ").append(i).append('\n');
		}
		byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[body.length + 3];
		bytes[0] = (byte) 0xEF;
		bytes[1] = (byte) 0xBB;
		bytes[2] = (byte) 0xBF;
		System.arraycopy(body, 0, bytes, 3, body.length);
		
		TEFDocument expected = TEFReader.read(DOC, bytes);
		final byte[] source = bytes;
		InputStream oneByteAtATime = new InputStream() {
			private int pos;
			
			@Override
			public int read() {
				return pos < source.length ? (source[pos++] & 0xFF) : -1;
			}
			
			@Override
			public int read(byte[] b, int off, int len) {
				if (pos >= source.length) {
					return -1;
				}
				b[off] = source[pos++];
				return 1;
			}
		};
		final List<TEFEntry> entries = new ArrayList<TEFEntry>();
		TEFReader.read(DOC, oneByteAtATime, new TEFSink() {
			@Override
			public void documentComment(TEFComment comment) {
			}
			
			@Override
			public void documentDirective(TEFDirective directive) {
			}
			
			@Override
			public void entry(TEFEntry entry) {
				entries.add(entry);
			}
		});
		assertEquals(expected.getEntries().size(), entries.size(), "entry count");
		TEFEntry entry = entries.get(0);
		assertEquals("part", entry.getType(), "entry type survives BOM stripping");
		assertEquals("Bob", entry.getHeaders().get(0).getValue(), "header value");
		assertEquals(expected.getEntries().get(0).getContent(), entry.getContent(), "content");
		check(entry.getContent().contains("caf\u00e9 line 19999"), "content should reach the last line");
	}
	
	/** Collects what a TEFSink receives, for tests that inspect the stream. */
	private static final class RecordingSink implements TEFSink {
		final List<TEFEntry> entries = new ArrayList<TEFEntry>();
		final List<TEFComment> comments = new ArrayList<TEFComment>();
		final List<TEFDirective> directives = new ArrayList<TEFDirective>();
		
		@Override
		public void documentComment(TEFComment comment) {
			comments.add(comment);
		}
		
		@Override
		public void documentDirective(TEFDirective directive) {
			directives.add(directive);
		}
		
		@Override
		public void entry(TEFEntry entry) {
			entries.add(entry);
		}
	}
	
	/** Feeds bytes to a parser in blocks of the given size, then ends the input. */
	private static RecordingSink parseInBlocks(byte[] bytes, int blockSize) throws Exception {
		RecordingSink sink = new RecordingSink();
		Sink<byte[]> parser = TEFReader.parser(DOC, sink);
		for (int offset = 0; offset < bytes.length; offset += blockSize) {
			int end = Math.min(bytes.length, offset + blockSize);
			parser.accept(Optional.of(Arrays.copyOfRange(bytes, offset, end)));
		}
		parser.accept(Optional.<byte[]>empty());
		return sink;
	}
	
	private static String describe(List<TEFEntry> entries) {
		StringBuilder out = new StringBuilder();
		for (TEFEntry entry : entries) {
			out.append(entry.getType()).append(' ').append(entry.getValue()).append('\n');
			for (TEFHeader header : entry.getHeaders()) {
				out.append(header.getKey()).append('=').append(header.getValue()).append('\n');
			}
			out.append(entry.getContent()).append("\n--\n");
		}
		return out.toString();
	}
	
	private static void testTefUtf8SplitAcrossBlocks() throws Exception {
		String[] samples = {"caf\u00e9", "\u20ac", "\ud83d\ude00"};
		for (String sample : samples) {
			String text = "\uFEFF=part p1\nname: " + sample + " x\n\n" + sample + sample + "\n";
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			String whole = describe(parseInBlocks(bytes, bytes.length).entries);
			for (int blockSize : new int[] {1, 2, 3, 5, 7}) {
				assertEquals(whole, describe(parseInBlocks(bytes, blockSize).entries),
						"parse with blocks of " + blockSize + " for " + sample);
			}
		}
	}
	
	private static void testTefInvalidUtf8SplitAcrossBlocks() throws Exception {
		byte[][] invalid = {
				{(byte) 0xE2, (byte) 0x82},
				{(byte) 0xE2, (byte) 0x28, (byte) 0xA1},
				{(byte) 0xF0, (byte) 0x9F, (byte) 0x98},
		};
		byte[] prefix = "=part p1\nname: ".getBytes(StandardCharsets.UTF_8);
		byte[] newline = {(byte) '\n'};
		for (byte[] badSequence : invalid) {
			byte[] bytes = concat(prefix, badSequence, newline);
			for (int blockSize : new int[] {1, 2, bytes.length}) {
				boolean threw = false;
				try {
					parseInBlocks(bytes, blockSize);
				} catch (TEFReadException expected) {
					threw = true;
					check(expected.getMessage().contains("Invalid UTF-8"),
							"error should be invalid UTF-8: " + expected.getMessage());
				}
				check(threw, "invalid UTF-8 should be rejected with blocks of " + blockSize);
			}
		}
		
		byte[] truncated = concat(prefix, new byte[] {(byte) 0xE2, (byte) 0x82}, new byte[0]);
		boolean threw = false;
		try {
			parseInBlocks(truncated, 1);
		} catch (TEFReadException expected) {
			threw = true;
		}
		check(threw, "input ending mid-character should be rejected");
	}
	
	private static byte[] concat(byte[]... parts) {
		int total = 0;
		for (byte[] part : parts) {
			total += part.length;
		}
		byte[] out = new byte[total];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, out, offset, part.length);
			offset += part.length;
		}
		return out;
	}
	
	private static void testTefEntriesArriveBeforeEnd() throws Exception {
		RecordingSink sink = new RecordingSink();
		Sink<byte[]> parser = TEFReader.parser(DOC, sink);
		parser.accept(Optional.of("=part a\nname: 1\n\nx\n=part b\nname: 2\n\n".getBytes(StandardCharsets.UTF_8)));
		assertEquals(1, sink.entries.size(), "first entry is delivered before end of input");
		assertEquals("a", sink.entries.get(0).getValue(), "first entry value");
		parser.accept(Optional.<byte[]>empty());
		assertEquals(2, sink.entries.size(), "second entry is delivered at end of input");
	}
	
	private static void testSourceProvenance() throws Exception {
		byte[] plain = "=part p1\n".getBytes(StandardCharsets.UTF_8);
		byte[] withBom = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, plain);
		
		SourceProvenance p = SourceProvenance.of(withBom);
		assertEquals(Identifiers.sha256(withBom), p.getSha256(), "sha256 of all bytes including BOM");
		assertEquals((long) withBom.length, p.getByteLength(), "byte length includes BOM");
		check(p.hasBom(), "BOM detected");
		check(!SourceProvenance.of(plain).hasBom(), "no BOM in plain input");
		
		SourceProvenance.Digester digester = new SourceProvenance.Digester();
		for (byte b : withBom) {
			digester.accept(Optional.of(new byte[] {b}));
		}
		digester.accept(Optional.<byte[]>empty());
		SourceProvenance streamed = digester.result();
		assertEquals(p.getSha256(), streamed.getSha256(), "streamed sha256");
		assertEquals(p.getByteLength(), streamed.getByteLength(), "streamed length");
		check(streamed.hasBom(), "BOM detected when split across blocks");
		
		boolean threw = false;
		try {
			new SourceProvenance.Digester().result();
		} catch (IllegalStateException expected) {
			threw = true;
		}
		check(threw, "result before end of input should be refused");
	}
	
	private static void testMultiSinkOrder() throws Exception {
		final List<String> log = new ArrayList<String>();
		Sink<String> first = new Sink<String>() {
			@Override
			public void accept(Optional<String> item) {
				log.add("first:" + item.orElse("<end>"));
			}
		};
		Sink<String> second = new Sink<String>() {
			@Override
			public void accept(Optional<String> item) {
				log.add("second:" + item.orElse("<end>"));
			}
		};
		Sink<String> both = new MultiSink<String>(Arrays.asList(first, second));
		both.accept(Optional.of("a"));
		both.accept(Optional.of("b"));
		both.accept(Optional.<String>empty());
		assertEquals("[first:a, second:a, first:b, second:b, first:<end>, second:<end>]",
				log.toString(), "every target sees every item and the end, in order");
	}
	
	private static void testTefSpans() throws Exception {
		TEFDocument document = readTef("=part p1310\nname: Widget\n\nSome content\n");
		TEFEntry entry = document.getEntries().get(0);
		assertEquals(1, entry.getSpan().startLine, "entry start line");
		assertEquals(1, entry.getSpan().startColumn, "entry start column");
		check(entry.getSpan().endLine >= 4, "entry span should cover content");
		check(
				entry.getSpan().toLocation().toString().startsWith("L1C1..."),
				"entry location should be a span starting at L1C1");
				
		TEFHeader header = entry.getHeaders().get(0);
		assertEquals(2, header.getSpan().startLine, "header start line");
		assertEquals(1, header.getSpan().startColumn, "header start column");
	}
	
	private static void testTefCrLf() throws Exception {
		TEFDocument document = readTef("=part p1\r\nname: Bob\r\n\r\ncontent\r\n");
		assertEquals(1, document.getEntries().size(), "entry count");
		
		TEFEntry entry = document.getEntries().get(0);
		assertEquals("Bob", entry.getHeaders().get(0).getValue(), "header value");
		assertEquals("content\r\n", entry.getContent(), "content preserves CRLF");
	}
	
	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder();
		for (byte b : bytes) {
			out.append(String.format("%02x", b & 0xFF));
		}
		return out.toString();
	}
	
	private static byte[] digest(MessageDigest digest, byte[] input) {
		digest.reset();
		return digest.digest(input);
	}
	
	private static void testHashKnownAnswers() throws Exception {
		// Tiger-192 test vectors from the Tiger specification.
		assertEquals("3293ac630c13f0245f92bbb1766e16167a4e58492dde73f3",
				hex(digest(new Tiger(), new byte[0])), "Tiger of empty input");
		assertEquals("2aab1484e8c158f2bfb8c5ff41b57a525129131c957b5f93",
				hex(digest(new Tiger(), "abc".getBytes(StandardCharsets.US_ASCII))), "Tiger of abc");
				
		// A tree hash of a single leaf is the leaf hash: Tiger over a 0x00 prefix and the data.
		check(Arrays.equals(digest(new Tiger(), new byte[] {0}), digest(new TigerTree(), new byte[0])),
				"TigerTree of empty input is the empty leaf hash");
				
		// RFC 4648 Base32 test vectors, without padding.
		assertEquals("MY", Base32.encode("f".getBytes(StandardCharsets.US_ASCII)), "Base32 of f");
		assertEquals("MZXW6YTB", Base32.encode("fooba".getBytes(StandardCharsets.US_ASCII)), "Base32 of fooba");
		assertEquals("MZXW6YTBOI", Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII)), "Base32 of foobar");
		
		// SHA-1 test vector, and the bitprint URN built from it.
		byte[] content = "abc".getBytes(StandardCharsets.US_ASCII);
		assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
				hex(digest(MessageDigest.getInstance("SHA-1"), content)), "SHA-1 of abc");
		String urn = BitprintScheme.instance.hashToUri(digest(new BitprintDigest(), content));
		check(urn.startsWith("urn:bitprint:"), "bitprint prefix: " + urn);
		assertEquals(13 + 32 + 1 + 39, urn.length(), "bitprint URN length");
		assertEquals(Base32.encode(digest(MessageDigest.getInstance("SHA-1"), content)),
				urn.substring(13, 13 + 32), "bitprint SHA-1 part");
	}
	
	private static void testIdentifiers() throws Exception {
		String url = "https://example.com/a b.tef";
		String documentIri = Identifiers.documentIri(url);
		String documentImportIri = Identifiers.documentImportIri(documentIri, "test/1");
		assertEquals(url, documentIri, "document IRI is the source URL");
		assertEquals(documentIri, Identifiers.documentIri(url), "document IRI is deterministic");
		assertEquals(documentImportIri, Identifiers.documentImportIri(documentIri, "test/1"),
				"document import IRI is deterministic");
		check(!documentImportIri.equals(Identifiers.documentImportIri(documentIri, "test/2")),
				"parser versions produce different document imports");
		check(!documentIri.equals(Identifiers.documentIri(url + "x")), "different URLs differ");
		
		String entryIri = Identifiers.tefEntryIri(documentIri, 7);
		check(entryIri.startsWith(url + "#tef-entry-"), "entry IRI prefix");
		check(entryIri.endsWith("7"), "entry ordinal suffix");
		
		assertEquals(
				"tag:nuke24.net,2026-11:togetl:part:a%2fb",
				Identifiers.mint("part", "a/b"),
				"slash is encoded");
		assertEquals(
				"tag:nuke24.net,2026-11:togetl:part:a:b",
				Identifiers.mint("part", "a:b"),
				"colon is kept");
	}
	
	private static void testGenericDialect() throws Exception {
		String text =
				"#format x\n"
				+ "#custom y\n"
				+ "# a note\n"
				+ "=part p1\n"
				+ "name: Widget\n"
				+ "tag: a\n"
				+ "tag: b\n"
				+ "\n"
				+ "content\n";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String sourceUrl = "https://example.com/data.tef";
		SourceDocument source = SourceDocument.of(sourceUrl, "tef", "test/1");
		ImportReport report = new ImportReport();
		
		List<Statement> statements = collect(new GenericTEFDialect(), bytes, source, report);
		String doc = source.getDocumentIri();
		String documentImport = source.getDocumentImportIri();
		String entry = Identifiers.tefEntryIri(doc, 0);
		assertEquals(statements.size(), countSource(statements, documentImport), "all statements use document import source");
		
		check(has(statements, doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT.toString()),
				"document type statement");
		check(has(statements, doc, Vocab.TOGETL_SHA256, Literal.of(Identifiers.sha256(bytes)).toString()),
				"document hash statement");
		check(has(statements, documentImport, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT_IMPORT.toString()),
				"document import type statement");
		check(has(statements, documentImport, Vocab.TOGETL_SOURCE_DOCUMENT, doc),
				"document import links to source document");
		check(has(statements, documentImport, Vocab.TOGETL_PARSER_VERSION, Literal.of("test/1").toString()),
				"document import parser version");
		check(has(statements, documentImport, Vocab.TOGETL_HAS_BOM,
				Literal.of("false", Vocab.XSD_BOOLEAN).toString()), "document import BOM flag");
		check(has(statements, entry, Vocab.TEF_ENTRY_TYPE, Literal.of("part").toString()),
				"entry type statement");
		check(has(statements, entry, Vocab.TEF_ENTRY_VALUE, Literal.of("p1").toString()),
				"entry value statement");
		check(has(statements, entry, Vocab.TEF_CONTENT, Literal.of("content\n").toString()),
				"entry content statement");
				
		assertEquals(2, countObject(statements, Vocab.TEF_KEY, Literal.of("tag").toString()),
				"both repeated headers captured");
		assertEquals(1, countObject(statements, Vocab.TEF_TEXT, Literal.of("# a note").toString()),
				"document comment captured");
		assertEquals(1, countObject(statements, Vocab.TEF_NAME, Literal.of("custom").toString()),
				"directive captured");
				
		check(reportHas(report, "unmapped-entry-type", "part", ""), "entry type reported");
		check(reportHas(report, "unmapped-field", "part", "name"), "name field reported");
		check(reportHas(report, "unmapped-field", "part", "tag"), "tag field reported");
		check(reportHas(report, "unmapped-directive", "", "custom"), "custom directive reported");
		check(!reportHas(report, "unmapped-directive", "", "format"), "format directive is known");
	}
	
	private static void testGenericDialectRoundTrip() throws Exception {
		String text = "=part p1\nname: Widget\ntag: a\ntag: b\n\ncontent\n";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String sourceUrl = "https://example.com/data.tef";
		SourceDocument source = SourceDocument.of(sourceUrl, "tef", "test/1");
		ImportReport report = new ImportReport();
		
		List<Statement> statements = collect(new GenericTEFDialect(), bytes, source, report);
		StringWriter out = new StringWriter();
		TOGETLTSVWriter writer = new TOGETLTSVWriter(out, AliasTable.withDefaults());
		try {
			for (Statement statement : statements) {
				writer.write(statement);
			}
		} finally {
			writer.close();
		}
		
		List<Statement> read = TOGETLTSVReader.readAll(new StringReader(out.toString()));
		assertEquals(statements, read, "generic statements round trip");
	}
	
	private static void testPartsMapping() throws Exception {
		String text =
				"=part p1\n"
				+ "short-description: Widget\n"
				+ "tags: a, b\n"
				+ "member-of: p2\n"
				+ "stl-file: p1.stl\turn:bitprint:ABC\n"
				+ "openscad-rendering-ref: http://example.com/p1.png\n"
				+ "mystery: value\n"
				+ "\n"
				+ "A part.\n";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String sourceUrl = "https://example.com/data.tef";
		SourceDocument source = SourceDocument.of(sourceUrl, "tef", "test/1");
		ImportReport report = new ImportReport();
		MappingTEFDialect dialect = new MappingTEFDialect(
				"parts", MappingTable.load("parts"));
				
		List<Statement> statements = collect(dialect, bytes, source, report);
		String part = Identifiers.mintData("part", "p1");
		
		check(has(statements, part, Vocab.RDF_TYPE, Vocab.TOGETL_PART.toString()),
				"part type");
		check(has(statements, part, Vocab.TOGETL_SHORT_DESCRIPTION,
				Literal.of("Widget").toString()), "short description");
		check(has(statements, part, Vocab.TOGETL_TAG, Literal.of("a").toString()),
				"first tag");
		check(has(statements, part, Vocab.TOGETL_TAG, Literal.of("b").toString()),
				"second tag");
		check(has(statements, part, Vocab.TOGETL_IS_MEMBER_OF,
				Identifiers.referenceIri("p2")), "member-of resolves to a part IRI");
		check(!has(statements, part, Vocab.TOGETL_DOCUMENT_LINK, source.getDocumentIri()),
				"mapped part should not be linked to source TEF document");
		String stlResource = "http://wherever-files.nuke24.net/uri-res/raw/urn:bitprint:ABC/p1.stl";
		check(has(statements, part, Vocab.TOGETL_STL_FILE, stlResource),
				"stl file resource URL");
		check(!has(statements, stlResource, Vocab.TOGETL_FILENAME,
				Literal.of("p1.stl").toString()), "canonical file URL does not duplicate filename");
		check(!has(statements, stlResource, Vocab.RDF_TYPE,
				Vocab.TOGETL_FILE.toString()), "canonical file URL does not duplicate file type");
		check(has(statements, part, Vocab.TOGETL_OPENSCAD_RENDERING,
				"http://example.com/p1.png"), "rendering reference");
		check(has(statements, part, Vocab.RDFS_COMMENT,
				Literal.of("A part.\n").toString()), "content becomes a comment");
		check(reportHas(report, "unmapped-field", "part", "mystery"),
				"unknown field is reported");
		assertEquals(0, countObject(statements, Vocab.TEF_KEY, Literal.of("mystery").toString()),
				"unknown field is not emitted as TEF structure in mapped dialect");
		check(!has(statements, part, Vocab.TEF_ENTRY_TYPE, Literal.of("part").toString()),
				"mapped subject does not get TEF entry metadata");
	}
	
	private static void testPartsConflicts() throws Exception {
		String text =
				"=part p1\n"
				+ "short-description: first\n"
				+ "tags: a\n"
				+ "\n"
				+ "=part p1\n"
				+ "short-description: second\n"
				+ "tags: b\n"
				+ "\n"
				+ "=part p2\n"
				+ "short-description: same\n"
				+ "\n"
				+ "=part p2\n"
				+ "short-description: same\n";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String sourceUrl = "https://example.com/conflicts.tef";
		SourceDocument source = SourceDocument.of(sourceUrl, "tef", "test/1");
		ImportReport report = new ImportReport();
		MappingTEFDialect dialect = new MappingTEFDialect(
				"parts", MappingTable.load("parts"));
				
		List<Statement> statements = collect(dialect, bytes, source, report);
		
		assertEquals(1, countObject(statements, Vocab.TOGETL_SHORT_DESCRIPTION,
				Literal.of("first").toString()), "first value kept");
		assertEquals(1, countObject(statements, Vocab.TOGETL_SHORT_DESCRIPTION,
				Literal.of("second").toString()), "conflicting value kept");
		check(reportHas(report, "conflict", "part", "short-description"),
				"functional conflict is reported");
		assertEquals(1, countObject(statements, Vocab.TOGETL_TAG, Literal.of("a").toString()),
				"multi-valued field keeps a");
		assertEquals(1, countObject(statements, Vocab.TOGETL_TAG, Literal.of("b").toString()),
				"multi-valued field keeps b");
		assertEquals(1, countObject(statements, Vocab.TOGETL_SHORT_DESCRIPTION,
				Literal.of("same").toString()), "identical duplicate is deduplicated");
	}
	
	private static void testNamespacesMapping() throws Exception {
		String text =
				"=namespace http://example.com/ns/ \n"
				+ "project-homepage-url: https://example.com/\n"
				+ "short-description: Example namespace\n"
				+ "\n"
				+ "=type http://example.com/ns/Thing\n"
				+ "short-description: A thing.\n"
				+ "\n"
				+ "=attribute http://example.com/ns/prop\n"
				+ "=datatype http://example.com/ns/Type\n"
				+ "=format http://example.com/ns/Format\n"
				+ "=projbase urn:oid:1.2.3\n"
				+ "title: Project\n";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		String sourceUrl = "https://example.com/namespaces.tef";
		SourceDocument source = SourceDocument.of(sourceUrl, "tef", "test/1");
		ImportReport report = new ImportReport();
		MappingTEFDialect dialect = new MappingTEFDialect(
				"namespaces", MappingTable.load("namespaces"));
				
		List<Statement> statements = collect(dialect, bytes, source, report);
		
		check(has(statements, "http://example.com/ns/", Vocab.RDF_TYPE,
				Vocab.TOGETL_NAMESPACE.toString()), "namespace type and trimmed subject");
		check(has(statements, "http://example.com/ns/", Vocab.TOGETL_PROJECT_HOMEPAGE,
				"https://example.com/"), "project homepage");
		check(has(statements, "http://example.com/ns/Thing", Vocab.RDF_TYPE,
				Vocab.RDFS_CLASS.toString()), "type is a class");
		check(has(statements, "http://example.com/ns/prop", Vocab.RDF_TYPE,
				Vocab.RDF_PROPERTY.toString()), "attribute is a property");
		check(has(statements, "http://example.com/ns/Type", Vocab.RDF_TYPE,
				Vocab.RDFS_DATATYPE.toString()), "datatype is a datatype");
		check(has(statements, "http://example.com/ns/Format", Vocab.RDF_TYPE,
				Vocab.TOGETL_FORMAT_CLASS.toString()), "format is a format");
		check(has(statements, "urn:oid:1.2.3", Vocab.RDF_TYPE,
				Vocab.TOGETL_PROJECT_BASE.toString()), "project base");
		check(has(statements, "urn:oid:1.2.3", Vocab.TOGETL_TITLE,
				Literal.of("Project").toString()), "project title");
	}
	
	private static void testVocabularyReport() throws Exception {
		Vocabulary vocabulary = VocabularyLoader.loadDefaults();
		List<MappingTable> tables = new ArrayList<MappingTable>();
		for (Path path : MappingTable.defaultFiles()) {
			tables.add(MappingTable.load(path.toString()));
		}
		VocabularyReport report = VocabularyReport.analyze(vocabulary, tables);
		
		check(vocabulary.size() > 0, "vocabulary definitions were loaded");
		check(!report.hasUndefined(), "all mapping terms are defined");
		assertEquals(0, report.count(VocabularyReport.UNDEFINED), "no undefined terms");
		assertEquals(0, report.count(VocabularyReport.PROPOSED), "no proposed terms");
		assertEquals(0, report.count(VocabularyReport.DUPLICATE), "no duplicate terms");
		
		boolean found = false;
		for (VocabularyReport.Row row : report.getRows()) {
			if (row.getTerm().equals(Vocab.TOGETL_SHORT_DESCRIPTION.toString())) {
				found = true;
				assertEquals(VocabularyReport.OK, row.getStatus(), "shortDescription status");
			}
		}
		check(found, "shortDescription appears in the report");
	}
	
	private static void testVocabCommand() throws Exception {
		Path dir = Files.createTempDirectory("togetl-vocab-test");
		Path report = dir.resolve("vocab-report.tsv");
		dir.toFile().deleteOnExit();
		report.toFile().deleteOnExit();
		
		assertEquals(0, VocabCommand.run(new String[] {
				"--report", report.toString()
		}), "vocab command exit code");
		
		List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
		assertEquals("term\tstatus\tusedBy\tdefinedBy\tnote", lines.get(0), "vocab report header");
		check(lines.size() > 1, "vocab report has rows");
	}
	
	private static void testOrgReader() {
		String org = ""
				+ "* TODO [#A] Root heading :root:test:\n"
				+ ":PROPERTIES:\n"
				+ ":CUSTOM_ID: root-id\n"
				+ ":slicer_xy_compensation: -0.15\n"
				+ ":END:\n"
				+ "SCHEDULED: <2026-11-12 Thu>\n"
				+ "[2026-11-13 Fri] body note\n"
				+ "- [X] done item\n"
				+ "See [[https://example.com/][Example]]\n"
				+ "#+begin_src org\n"
				+ "* Not a heading\n"
				+ "[[https://example.com/fake][Fake]]\n"
				+ "- [ ] not a checkbox\n"
				+ "#+end_src\n"
				+ "** Child heading\n"
				+ "- [ ] open item\n";
		OrgDocument document = OrgReader.read(org.getBytes(StandardCharsets.UTF_8));
		assertEquals(2, document.getHeadings().size(), "heading count");
		
		OrgHeading root = document.getHeadings().get(0);
		assertEquals(1, root.getLevel(), "root level");
		assertEquals("Root heading", root.getTitle(), "root title");
		assertEquals("TODO", root.getTodoKeyword(), "root TODO keyword");
		assertEquals("A", root.getPriority(), "root priority");
		assertEquals(2, root.getTags().size(), "root tag count");
		assertEquals("root", root.getTags().get(0), "first root tag");
		assertEquals("test", root.getTags().get(1), "second root tag");
		assertEquals("root-id", root.getCustomId(), "CUSTOM_ID parsed");
		assertEquals(2, root.getProperties().size(), "property count");
		assertEquals("-0.15", root.getProperty("slicer_xy_compensation"), "custom property");
		assertEquals(1, root.getPlanning().size(), "planning count");
		assertEquals("SCHEDULED", root.getPlanning().get(0).getKeyword(), "planning keyword");
		assertEquals("<2026-11-12 Thu>", root.getPlanning().get(0).getTimestamp().getText(),
				"planning timestamp");
		assertEquals(1, root.getTimestamps().size(), "body timestamp count");
		assertEquals("[2026-11-13 Fri]", root.getTimestamps().get(0).getText(), "body timestamp text");
		assertEquals(1, root.getCheckboxes().size(), "root checkbox count");
		assertEquals("checked", root.getCheckboxes().get(0).getState(), "root checkbox state");
		assertEquals(1, root.getLinks().size(), "root link count");
		assertEquals("https://example.com/", root.getLinks().get(0).getTarget(), "link target");
		
		OrgHeading child = document.getHeadings().get(1);
		assertEquals(2, child.getLevel(), "child level");
		assertEquals(1, child.getCheckboxes().size(), "child checkbox count");
		assertEquals("unchecked", child.getCheckboxes().get(0).getState(), "child checkbox state");
	}
	
	private static void testOrgHeadingMetadata() {
		String org = ""
				+ "#+TODO: TODO STARTED | DONE CANCELLED\n"
				+ "* STARTED [#B] Configured keyword :project:\n"
				+ "DEADLINE: <2026-12-01 Tue> CLOSED: [2026-11-30 Mon]\n"
				+ "* LEGO-compatible bricks\n"
				+ "* [2026-11-14 Sat] Date heading\n";
		OrgDocument document = OrgReader.read(org.getBytes(StandardCharsets.UTF_8));
		assertEquals(3, document.getHeadings().size(), "heading count");
		
		OrgHeading configured = document.getHeadings().get(0);
		assertEquals("STARTED", configured.getTodoKeyword(), "configured TODO keyword");
		assertEquals("B", configured.getPriority(), "configured priority");
		assertEquals("Configured keyword", configured.getTitle(), "configured title");
		assertEquals(1, configured.getTags().size(), "configured tag count");
		assertEquals("project", configured.getTags().get(0), "configured tag");
		assertEquals(2, configured.getPlanning().size(), "planning entry count");
		assertEquals("DEADLINE", configured.getPlanning().get(0).getKeyword(), "deadline keyword");
		assertEquals("CLOSED", configured.getPlanning().get(1).getKeyword(), "closed keyword");
		
		OrgHeading plain = document.getHeadings().get(1);
		assertEquals("LEGO-compatible bricks", plain.getTitle(), "uppercase title is not a TODO keyword");
		assertEquals(null, plain.getTodoKeyword(), "plain heading has no TODO keyword");
		
		OrgHeading dated = document.getHeadings().get(2);
		assertEquals("[2026-11-14 Sat] Date heading", dated.getTitle(), "date remains in heading title");
		assertEquals(1, dated.getTimestamps().size(), "heading-line timestamp parsed");
	}
	
	private static void testOrgDirectivesAndTables() {
		String org = ""
				+ "#+TITLE: Example\n"
				+ "#+NAME: item_0\n"
				+ "#+CAPTION: Items\n"
				+ "| Desc          | STL |\n"
				+ "|---------------+-----|\n"
				+ "| cup           | p1  |\n"
				+ "| [[#custom-id]] |     |\n"
				+ "\n"
				+ "* Heading\n"
				+ "#+NAME: printer_1\n"
				+ "| Printer | Item       |\n"
				+ "|---------+------------|\n"
				+ "| KM      | WSITEM-6013 |\n"
				+ "#+TBLFM: $2=upper($1)\n";
		OrgDocument document = OrgReader.read(org.getBytes(StandardCharsets.UTF_8));
		assertEquals(1, document.getHeadings().size(), "heading count");
		assertEquals(5, document.getDirectives().size(), "directive count");
		assertEquals(2, document.getTables().size(), "table count");
		
		OrgTable first = document.getTables().get(0);
		assertEquals("item_0", first.getName(), "first table name");
		assertEquals("Items", first.getCaption(), "first table caption");
		assertEquals(-1, first.getHeadingIndex(), "document-level table");
		assertEquals(4, first.getRows().size(), "first table row count");
		assertEquals(2, first.getWidth(), "first table width");
		OrgTableRow header = first.getRows().get(0);
		assertEquals(false, header.isSeparator(), "header is not a separator");
		assertEquals("Desc", header.getCell(0), "first header cell");
		assertEquals("STL", header.getCell(1), "second header cell");
		check(first.getRows().get(1).isSeparator(), "separator row detected");
		assertEquals("cup", first.getRows().get(2).getCell(0), "data cell");
		assertEquals("[[#custom-id]]", first.getRows().get(3).getCell(0), "link cell preserved");
		assertEquals("", first.getRows().get(3).getCell(1), "empty cell preserved");
		
		OrgTable second = document.getTables().get(1);
		assertEquals("printer_1", second.getName(), "second table name");
		assertEquals(0, second.getHeadingIndex(), "heading-level table");
		assertEquals("WSITEM-6013", second.getRows().get(2).getCell(1), "second table data cell");
		
		assertEquals("Example", document.getDirectives().get(0).getValue(), "first directive value");
		assertEquals("TBLFM", document.getDirectives().get(4).getName(), "last directive name");
		assertEquals(0, document.getDirectives().get(4).getHeadingIndex(), "directive heading index");
	}
	
	private static void checkOrgError(String text, int line, String fragment) {
		boolean threw = false;
		try {
			OrgReader.read(text.getBytes(StandardCharsets.UTF_8));
		} catch (OrgSyntaxException expected) {
			threw = true;
			assertEquals(line, expected.getLine(), "org error line");
			check(expected.getMessage().contains(fragment), "org error should mention " + fragment);
		}
		check(threw, "org syntax error should be fatal: " + fragment);
	}
	
	private static void testOrgRecovery() {
		OrgDocument bom = OrgReader.read("\uFEFF* A\n".getBytes(StandardCharsets.UTF_8));
		check(bom.hasBom(), "byte order mark detected");
		
		checkOrgError("* A\n#+begin_src\n* B\n* C\n", 2, "unclosed block");
		checkOrgError("* A\n:PROPERTIES:\n:CUSTOM_ID: a\n* B\n", 2, "unclosed drawer");
		checkOrgError("* A\n:notes:\nno end here\n", 2, "unclosed drawer");
		
		OrgDocument tables = OrgReader.read(
				"* T\n|-|-|\n| a | b |\n|---+---|\n".getBytes(StandardCharsets.UTF_8));
		OrgTable table = tables.getTables().get(0);
		assertEquals(3, table.getRows().size(), "table row count");
		check(table.getRows().get(0).isSeparator(), "|-|-| is a separator");
		check(!table.getRows().get(1).isSeparator(), "data row is not a separator");
		check(table.getRows().get(2).isSeparator(), "|---+---| is a separator");
		assertEquals(0, tables.getDiagnostics().size(), "well-formed document has no diagnostics");
	}
	
	private static void testOrgLineEndingsAndCells() {
		OrgDocument crlf = OrgReader.read(
				"* A\r\n:PROPERTIES:\r\n:CUSTOM_ID: a\r\n:END:\r\n* B\r\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(2, crlf.getHeadings().size(), "CRLF heading count");
		assertEquals("a", crlf.getHeadings().get(0).getProperty("CUSTOM_ID"), "CRLF property value");
		assertEquals(5, crlf.getHeadings().get(1).getLine(), "CRLF heading line number");
		assertEquals("B", crlf.getHeadings().get(1).getTitle(), "CRLF heading title has no CR");
		
		OrgDocument loneCr = OrgReader.read("* A\rstill A\n* B\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(2, loneCr.getHeadings().size(), "lone CR does not end a line");
		assertEquals(2, loneCr.getHeadings().get(1).getLine(), "lone CR does not add a line");
		
		OrgDocument cells = OrgReader.read(
				"* T\n| a \\| b | c |\n|-foo|\n".getBytes(StandardCharsets.UTF_8));
		OrgTable table = cells.getTables().get(0);
		assertEquals(2, table.getRows().size(), "table row count");
		OrgTableRow row = table.getRows().get(0);
		assertEquals(3, row.getCellCount(), "a bare pipe always separates cells, as in Org");
		assertEquals("a \\", row.getCell(0), "cell before the pipe keeps its backslash");
		assertEquals("b", row.getCell(1), "cell after the pipe");
		check(table.getRows().get(1).isSeparator(), "line starting with |- is a horizontal rule");
		
		OrgDocument notes = OrgReader.read(
				"* T\n| a | b | free note\n| c | d | e |\n".getBytes(StandardCharsets.UTF_8));
		OrgTable noteTable = notes.getTables().get(0);
		assertEquals(2, noteTable.getRows().size(), "row without trailing pipe is still a row");
		OrgTableRow noteRow = noteTable.getRows().get(0);
		assertEquals(3, noteRow.getCellCount(), "unterminated last cell is a cell");
		assertEquals("free note", noteRow.getCell(2), "free-form last cell text");
		assertEquals(3, noteTable.getRows().get(1).getCellCount(), "terminated row unchanged");
		
		OrgDocument unterminated = OrgReader.read("* A\n* B".getBytes(StandardCharsets.UTF_8));
		assertEquals(2, unterminated.getHeadings().size(), "unterminated last line is parsed");
		
		try {
			new OrgParser().document();
			check(false, "document() before end of input must fail");
		} catch (IllegalStateException expected) {
			check(true, "document() before end of input fails");
		}
	}
	
	private static void testOrgChunkedInput() throws Exception {
		String org = "\uFEFF* A\r\n:PROPERTIES:\r\n:CUSTOM_ID: a\r\n:END:\r\n* B\r\n";
		Reader oneChar = new FilterReader(new StringReader(org)) {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				return super.read(buffer, offset, Math.min(1, length));
			}
		};
		OrgDocument document = OrgReader.read(oneChar);
		check(document.hasBom(), "chunked input detects byte order mark");
		assertEquals(2, document.getHeadings().size(), "chunked heading count");
		assertEquals("a", document.getHeadings().get(0).getProperty("CUSTOM_ID"),
				"chunked property value");
		assertEquals(5, document.getHeadings().get(1).getLine(), "chunked line number");
	}
	
	private static void testOrgKeywordsAndDuplicates() throws Exception {
		OrgDocument bare = OrgReader.read("* TODO\n* WAIT thing\n".getBytes(StandardCharsets.UTF_8));
		OrgHeading bareTodo = bare.getHeadings().get(0);
		assertEquals("TODO", bareTodo.getTodoKeyword(), "bare keyword is a keyword");
		assertEquals("", bareTodo.getTitle(), "bare keyword has an empty title");
		assertEquals("WAIT", bare.getHeadings().get(1).getTodoKeyword(),
				"default keywords apply when no directive is present");
				
		OrgDocument custom = OrgReader.read((
				"* SHIP it\n"
				+ "* WAIT thing\n"
				+ "#+TODO: SHIP | DONE\n"
				+ "* SHIP [#A] task\n"
				+ "* TODO task\n").getBytes(StandardCharsets.UTF_8));
		assertEquals("SHIP", custom.getHeadings().get(0).getTodoKeyword(),
				"directive keywords apply to headings before it");
		assertEquals(null, custom.getHeadings().get(1).getTodoKeyword(),
				"defaults are replaced when a directive is present");
		assertEquals("WAIT thing", custom.getHeadings().get(1).getTitle(),
				"non-keyword word stays in the title");
		assertEquals("SHIP", custom.getHeadings().get(2).getTodoKeyword(), "keyword before priority");
		assertEquals("A", custom.getHeadings().get(2).getPriority(), "priority after keyword");
		assertEquals("task", custom.getHeadings().get(2).getTitle(), "title after priority");
		assertEquals(null, custom.getHeadings().get(3).getTodoKeyword(),
				"TODO is not a keyword once a directive replaces the defaults");
		assertEquals("TODO task", custom.getHeadings().get(3).getTitle(),
				"TODO stays in the title");
				
		OrgDocument duplicates = OrgReader.read((
				"* A\n:PROPERTIES:\n:CUSTOM_ID: x\n:END:\n"
				+ "* B\n:PROPERTIES:\n:CUSTOM_ID: x\n:END:\n").getBytes(StandardCharsets.UTF_8));
		assertEquals(1, duplicates.getDiagnostics().size(), "duplicate CUSTOM_ID diagnostic count");
		assertEquals(7, duplicates.getDiagnostics().get(0).getLine(), "duplicate CUSTOM_ID line");
	}
	
	private static void testOrgTableSeparatorAndInputRules() throws Exception {
		OrgDocument degrees = OrgReader.read("* T\n|-5 degrees|\n".getBytes(StandardCharsets.UTF_8));
		OrgTable table = degrees.getTables().get(0);
		assertEquals(1, table.getRows().size(), "|-5 degrees| is one row");
		check(table.getRows().get(0).isSeparator(),
				"any line starting with |- is a horizontal rule, even with text in it");
				
		try {
			new OrgParser().accept(Optional.of("embedded\nnewline"));
			check(false, "a line containing LF must be rejected");
		} catch (IllegalArgumentException expected) {
			check(true, "a line containing LF is rejected");
		}
	}
	
	private static void testImportCommand() throws Exception {
		Path dir = Files.createTempDirectory("togetl-test");
		Path input = dir.resolve("data.tef");
		Path first = dir.resolve("first.togetltsv");
		Path second = dir.resolve("second.togetltsv");
		Path report = dir.resolve("report.tsv");
		dir.toFile().deleteOnExit();
		input.toFile().deleteOnExit();
		first.toFile().deleteOnExit();
		second.toFile().deleteOnExit();
		report.toFile().deleteOnExit();
		
		String text = "=part p1\nname: Widget\n\ncontent\n";
		Files.write(input, text.getBytes(StandardCharsets.UTF_8));
		
		assertEquals(0, ImportCommand.run(new String[] {
				"--input", input.toString(),
				"--output", first.toString(),
				"--report", report.toString(),
				"--source-url", "file:///data.tef"
		}), "first import exit code");
		
		assertEquals(0, ImportCommand.run(new String[] {
				"--input", input.toString(),
				"--output", second.toString(),
				"--source-url", "file:///data.tef"
		}), "second import exit code");
		
		byte[] firstBytes = Files.readAllBytes(first);
		byte[] secondBytes = Files.readAllBytes(second);
		check(java.util.Arrays.equals(firstBytes, secondBytes), "import output is deterministic");
		
		List<Statement> read = TOGETLTSVReader.readAll(
				new StringReader(new String(firstBytes, StandardCharsets.UTF_8)));
		check(read.size() > 0, "import produced statements");
		
		List<String> reportLines = Files.readAllLines(report, StandardCharsets.UTF_8);
		assertEquals(
				"kind\tentryType\tfield\tcount\tfirstLocation",
				reportLines.get(0),
				"report header");
		check(reportLines.size() > 1, "report has rows");
	}
	
	private static Path tempDir() throws IOException {
		Path dir = Files.createTempDirectory("togetl-test");
		dir.toFile().deleteOnExit();
		return dir;
	}
	
	private static void testImportNoOutputOnError() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("broken.tef");
		Path output = dir.resolve("broken.togetltsv");
		Files.write(input, "=part p1\nname: Bob\nno colon here\n".getBytes(StandardCharsets.UTF_8));
		
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString()
		}), "syntax error exit code");
		check(!Files.exists(output), "partial output should be removed after a syntax error");
	}
	
	private static void testImportRejectsTolerance() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path output = dir.resolve("data.togetltsv");
		Files.write(input, "=part p1\nname: Bob\n".getBytes(StandardCharsets.UTF_8));
		
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(), "--tolerance", "warn"
		}), "--tolerance exit code");
		check(!Files.exists(output), "no output should be written for a rejected option");
	}
	
	private static void testImportWritesProvenance() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path output = dir.resolve("data.togetltsv");
		byte[] bytes = "=part p1\nname: Widget\n".getBytes(StandardCharsets.UTF_8);
		Files.write(input, bytes);
		
		assertEquals(0, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(),
				"--source-url", "file:///data.tef"
		}), "import exit code");
		String written = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
		check(written.contains(Identifiers.sha256(bytes)), "output should record the input's SHA-256");
		check(written.contains("data:," + bytes.length + "|"), "output should record the input's byte length");
	}
	
	private static void testImportFromStdin() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path fromFile = dir.resolve("file.togetltsv");
		Path fromStdin = dir.resolve("stdin.togetltsv");
		byte[] bytes = "=part p1\nname: Widget\n".getBytes(StandardCharsets.UTF_8);
		Files.write(input, bytes);
		
		assertEquals(0, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", fromFile.toString(),
				"--source-url", "file:///data.tef"
		}), "file import exit code");
		
		InputStream originalIn = System.in;
		System.setIn(new ByteArrayInputStream(bytes));
		try {
			assertEquals(0, ImportCommand.run(new String[] {
					"--input", "-", "--output", fromStdin.toString(),
					"--source-url", "file:///data.tef"
			}), "stdin import exit code");
		} finally {
			System.setIn(originalIn);
		}
		check(Arrays.equals(Files.readAllBytes(fromFile), Files.readAllBytes(fromStdin)),
				"stdin import should match the file import (stdin is read once, in full)");
				
		assertEquals(1, ImportCommand.run(new String[] {"--input", "-", "--output", fromStdin.toString()}),
				"stdin without a source option should be refused");
	}
	
	private static void testImportDerivesBitprint() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path output = dir.resolve("data.togetltsv");
		byte[] bytes = "=part p1\nname: Widget\n".getBytes(StandardCharsets.UTF_8);
		Files.write(input, bytes);
		String expected = BitprintScheme.instance.hashToUri(digest(new BitprintDigest(), bytes));
		
		assertEquals(0, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(),
				"--derive-source-url", "bitprint"
		}), "derived import exit code");
		String written = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
		check(written.contains(expected), "output should use the derived bitprint as source: " + expected);
	}
	
	private static void testImportRejectsMismatchedBitprint() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path output = dir.resolve("data.togetltsv");
		Files.write(input, "=part p1\nname: Widget\n".getBytes(StandardCharsets.UTF_8));
		
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(),
				"--source-url", "urn:bitprint:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
		}), "mismatched bitprint exit code");
		check(!Files.exists(output), "no output should be written for a mismatched bitprint");
	}
	
	private static void testImportRejectsBadSourceOptions() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("data.tef");
		Path output = dir.resolve("data.togetltsv");
		Files.write(input, "=part p1\nname: Widget\n".getBytes(StandardCharsets.UTF_8));
		
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(),
				"--derive-source-url", "sha1"
		}), "unknown derive kind exit code");
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString(),
				"--source-url", "file:///data.tef", "--derive-source-url", "bitprint"
		}), "both source options exit code");
		assertEquals(1, ImportCommand.run(new String[] {
				"--input", input.toString(), "--output", output.toString()
		}), "no source option exit code");
		check(!Files.exists(output), "no output should be written for rejected options");
	}
	
	/**
	 * Imports a generated document of about 20 MB in a child JVM with a 48 MB
	 * heap. Holding the whole input or the whole document in memory fails here.
	 */
	private static void testImportStreamsInSmallHeap() throws Exception {
		Path dir = tempDir();
		Path input = dir.resolve("large.tef");
		try (java.io.Writer out = Files.newBufferedWriter(input, StandardCharsets.UTF_8)) {
			out.write("#format http://ns.nuke24.net/TEF/Formats/SyntaxV0.3\n\n");
			for (int i = 0; i < 140000; i++) {
				out.write("=part p" + i + "\nname: Part number " + i + "\ntags: a, b\n\n");
				out.write("Some description of part " + i + ".\nIt has two lines.\n\n");
			}
		}
		
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		Process process = new ProcessBuilder(
				java, "-Xmx48m",
				"-cp", System.getProperty("java.class.path"),
				"net.nuke24.togetl.cli.Main", "import",
				"--dialect", "generic",
				"--source-url", "file:///large.tef",
				"--input", input.toString(),
				"--output", "-")
				.redirectError(dir.resolve("stderr.txt").toFile())
				.start();
		long discarded = 0;
		byte[] buffer = new byte[65536];
		try (InputStream stdout = process.getInputStream()) {
			int n;
			while ((n = stdout.read(buffer)) >= 0) {
				discarded += n;
			}
		}
		int exit = process.waitFor();
		String stderr = new String(Files.readAllBytes(dir.resolve("stderr.txt")), StandardCharsets.UTF_8);
		assertEquals(0, exit, "import exit code under -Xmx48m; stderr: " + stderr);
		check(discarded > 0, "import should write statements");
	}
	
	private static void testDatabaseLoad() throws Exception {
		Path dir = Files.createTempDirectory("togetl-db-test");
		Path db = dir.resolve("togetl.sqlite3");
		Path stream = dir.resolve("data.togetltsv");
		Path report = dir.resolve("report.tsv");
		dir.toFile().deleteOnExit();
		db.toFile().deleteOnExit();
		stream.toFile().deleteOnExit();
		report.toFile().deleteOnExit();
		
		Files.write(stream, databaseStream().getBytes(StandardCharsets.UTF_8));
		Files.write(report, (
				"kind\tentryType\tfield\tcount\tfirstLocation\n"
				+ "conflict\tpart\tshort-description\t2\tL3C1\n"
		).getBytes(StandardCharsets.UTF_8));
		
		String firstOutput = runLoad(db, stream, report, false);
		check(firstOutput.contains("Loaded 1 document(s), 20 statement(s)"),
				"first load summary: " + firstOutput);
		check(!firstOutput.contains("(replaced)"), "first load is not a replacement");
		
		Connection connection = SQLiteDatabase.open(db);
		try {
			assertDatabaseContents(connection);
		} finally {
			connection.close();
		}
		
		String secondOutput = runLoad(db, stream, report, false);
		check(secondOutput.contains("(replaced)"),
				"second load replaces the batch: " + secondOutput);
				
		connection = SQLiteDatabase.open(db);
		try {
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM document"),
					"document count after replacement");
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM batch"),
					"batch count after replacement");
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM loading"),
					"loading count after replacement");
			assertEquals(20L, queryLong(connection, "SELECT COUNT(*) FROM statement"),
					"statement count after replacement");
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM parse_issue"),
					"parse issue count after replacement");
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM import_report_row"),
					"report row count after replacement");
		} finally {
			connection.close();
		}
	}
	
	private static void assertDatabaseContents(Connection connection) throws Exception {
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM document"), "document count");
		assertEquals(DB_DOC, queryString(connection, "SELECT iri FROM document"), "document IRI");
		assertEquals("https://example.com/data.tef",
				queryString(connection, "SELECT source_url FROM document"), "document source URL");
		assertEquals("abc", queryString(connection, "SELECT sha256 FROM document"), "document hash");
		assertEquals(123L, queryLong(connection, "SELECT byte_length FROM document"),
				"document byte length");
		assertEquals("tef", queryString(connection, "SELECT detected_format FROM document"),
				"document format");
		assertEquals("test/1", queryString(connection, "SELECT parser_version FROM document"),
				"parser version");
				
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM batch"), "batch count");
		String batchIri = queryString(connection, "SELECT iri FROM batch");
		check(batchIri.startsWith(TOGETL + "batch:"), "batch IRI: " + batchIri);
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM loading"), "loading count");
		String loadingIri = queryString(connection, "SELECT iri FROM loading");
		check(loadingIri.startsWith(TOGETL + "loading:"), "loading IRI: " + loadingIri);
		assertEquals(batchIri, queryString(connection, "SELECT batch_iri FROM loading"),
				"loading batch");
		assertEquals(DB_DOC, queryString(connection, "SELECT document_iri FROM loading"),
				"loading document");
				
		assertEquals(20L, queryLong(connection, "SELECT COUNT(*) FROM statement"), "statement count");
		assertEquals(20L, queryLong(connection,
				"SELECT COUNT(*) FROM statement WHERE batch_iri = ? AND loading_iri = ?",
				batchIri, loadingIri), "statements carry batch and loading provenance");
				
		assertEquals("literal", queryString(connection,
				"SELECT object_kind FROM statement WHERE subject = ? AND predicate = ?",
				DB_DOC, GENERAL + "sha256"), "literal object kind");
		assertEquals(XSD + "integer", queryString(connection,
				"SELECT datatype FROM statement WHERE subject = ? AND predicate = ?",
				DB_DOC, GENERAL + "byteLength"), "literal datatype");
		assertEquals("iri", queryString(connection,
				"SELECT object_kind FROM statement WHERE subject = ? AND predicate = ?",
				DB_PART, PARTS3D + "stlFile"), "IRI object kind");
		assertEquals("blank", queryString(connection,
				"SELECT object_kind FROM statement WHERE subject = ? AND predicate = ?",
				DB_IMPORT, TOGETL + "parseIssue"), "blank node object kind");
				
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM parse_issue"),
				"parse issue count");
		assertEquals(DB_DOC, queryString(connection,
				"SELECT document_iri FROM parse_issue"), "issue document");
		assertEquals("warning", queryString(connection,
				"SELECT severity FROM parse_issue"), "issue severity");
		assertEquals("header-without-colon", queryString(connection,
				"SELECT code FROM parse_issue"), "issue code");
		assertEquals("Header line has no colon", queryString(connection,
				"SELECT message FROM parse_issue"), "issue message");
		assertEquals("name: Bob", queryString(connection,
				"SELECT raw FROM parse_issue"), "issue raw");
		assertEquals(batchIri, queryString(connection,
				"SELECT batch_iri FROM parse_issue"), "issue batch");
		assertEquals(loadingIri, queryString(connection,
				"SELECT loading_iri FROM parse_issue"), "issue loading");
				
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM import_report_row"),
				"report row count");
		assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM conflict"), "conflict count");
		assertEquals(DB_DOC, queryString(connection,
				"SELECT document_iri FROM import_report_row"), "report row document");
		assertEquals("part", queryString(connection,
				"SELECT entry_type FROM import_report_row"), "report entry type");
		assertEquals("short-description", queryString(connection,
				"SELECT field FROM import_report_row"), "report field");
				
		assertEquals(1L, queryLong(connection,
				"SELECT COUNT(*) FROM vocab_term WHERE iri = ?", GENERAL + "name"),
				"vocabulary term is loaded");
				
		assertEquals("Widget", queryString(connection,
				"SELECT name FROM part WHERE iri = ?", DB_PART), "part view name");
		assertEquals("A widget", queryString(connection,
				"SELECT short_description FROM part WHERE iri = ?", DB_PART),
				"part view description");
		assertEquals(PARTS3D + "stlFile", queryString(connection,
				"SELECT relation FROM part_file WHERE part_iri = ?", DB_PART),
				"part file relation");
		assertEquals(DB_STL_FILE, queryString(connection,
				"SELECT file_iri FROM part_file WHERE part_iri = ?", DB_PART),
				"part file IRI");
		assertEquals(null, queryString(connection,
				"SELECT filename FROM part_file WHERE part_iri = ?", DB_PART),
				"canonical file URL has no separate filename");
		assertEquals("p1-alias", queryString(connection,
				"SELECT alias FROM part_alias WHERE part_iri = ?", DB_PART),
				"part alias");
	}
	
	private static void testDatabaseSimpleProvenance() throws Exception {
		Path dir = Files.createTempDirectory("togetl-db-simple-test");
		Path db = dir.resolve("togetl.sqlite3");
		Path stream = dir.resolve("data.togetltsv");
		dir.toFile().deleteOnExit();
		db.toFile().deleteOnExit();
		stream.toFile().deleteOnExit();
		Files.write(stream, databaseStream().getBytes(StandardCharsets.UTF_8));
		
		String output = runLoad(db, stream, null, true);
		check(output.contains("provenance: document only"),
				"simple provenance summary: " + output);
				
		Connection connection = SQLiteDatabase.open(db);
		try {
			assertEquals(1L, queryLong(connection, "SELECT COUNT(*) FROM document"),
					"document count");
			assertEquals(0L, queryLong(connection, "SELECT COUNT(*) FROM batch"), "batch count");
			assertEquals(0L, queryLong(connection, "SELECT COUNT(*) FROM loading"), "loading count");
			assertEquals(20L, queryLong(connection, "SELECT COUNT(*) FROM statement"),
					"statement count");
			assertEquals(20L, queryLong(connection,
					"SELECT COUNT(*) FROM statement"
					+ " WHERE batch_iri IS NULL AND loading_iri IS NULL"),
					"statements have no batch or loading");
			assertEquals(1L, queryLong(connection,
					"SELECT COUNT(*) FROM parse_issue"
					+ " WHERE document_iri = ? AND batch_iri IS NULL AND loading_iri IS NULL",
					DB_DOC), "parse issue has document-only provenance");
			assertEquals("Widget", queryString(connection,
					"SELECT name FROM part WHERE iri = ?", DB_PART), "part view name");
		} finally {
			connection.close();
		}
	}
	
	private static String databaseStream() {
		return "#format urn:uuid:b783bac7-58e9-4340-93ef-7973914732d5\n"
				+ "#alias rdf: " + RDF + "\n"
				+ "#alias xsd: " + XSD + "\n"
				+ "#alias togetl: " + TOGETL + "\n"
				+ "#alias general: " + GENERAL + "\n"
				+ "#alias 3dparts: " + PARTS3D + "\n"
				+ "\n"
				+ DB_IMPORT + " - " + DB_DOC + " rdf:type togetl:Document\n"
				+ DB_IMPORT + " - " + DB_DOC
				+ " togetl:sourceUrl data:,https%3A%2F%2Fexample.com%2Fdata.tef\n"
				+ DB_IMPORT + " - " + DB_DOC + " general:sha256 data:,abc\n"
				+ DB_IMPORT + " - " + DB_DOC + " general:byteLength data:,123|xsd:integer\n"
				+ DB_IMPORT + " - " + DB_IMPORT + " rdf:type togetl:DocumentImport\n"
				+ DB_IMPORT + " - " + DB_IMPORT + " togetl:sourceDocument " + DB_DOC + "\n"
				+ DB_IMPORT + " - " + DB_IMPORT + " togetl:format data:,tef\n"
				+ DB_IMPORT + " - " + DB_IMPORT + " togetl:parserVersion data:,test/1\n"
				+ DB_IMPORT + " L1C1...L1C9 " + DB_PART + " rdf:type 3dparts:Part\n"
				+ DB_IMPORT + " L2C1...L2C13 " + DB_PART + " general:name data:,Widget\n"
				+ DB_IMPORT + " L3C1...L3C28 " + DB_PART
				+ " general:shortDescription data:,A%20widget\n"
				+ DB_IMPORT + " L4C1...L4C11 " + DB_PART + " general:tag data:,a\n"
				+ DB_IMPORT + " L5C1...L5C11 " + DB_PART + " general:alias data:,p1-alias\n"
				+ DB_IMPORT + " L6C1...L6C34 " + DB_PART + " 3dparts:stlFile " + DB_STL_FILE + "\n"
				+ DB_IMPORT + " L7C1...L7C2 " + DB_IMPORT + " togetl:parseIssue _:issue1\n"
				+ DB_IMPORT + " L7C1...L7C2 _:issue1 rdf:type togetl:ParseIssue\n"
				+ DB_IMPORT + " L7C1...L7C2 _:issue1 togetl:severity data:,warning\n"
				+ DB_IMPORT + " L7C1...L7C2 _:issue1 togetl:code data:,header-without-colon\n"
				+ DB_IMPORT + " L7C1...L7C2 _:issue1"
				+ " togetl:message data:,Header%20line%20has%20no%20colon\n"
				+ DB_IMPORT + " L7C1...L7C2 _:issue1 togetl:raw data:,name%3A%20Bob\n";
	}
	
	private static String runLoad(
			Path db,
			Path stream,
			Path report,
			boolean simpleProvenance
	) throws Exception {
		List<String> args = new ArrayList<String>();
		args.add("--db");
		args.add(db.toString());
		args.add("--input");
		args.add(stream.toString());
		if (report != null) {
			args.add("--report");
			args.add(report.toString());
		}
		if (simpleProvenance) {
			args.add("--simple-provenance");
		}
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream original = System.out;
		int exit;
		try {
			System.setOut(new PrintStream(bytes, true, "UTF-8"));
			exit = LoadCommand.run(args.toArray(new String[args.size()]));
		} finally {
			System.setOut(original);
		}
		String output = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
		assertEquals(0, exit, "load exit code: " + output);
		return output;
	}
	
	private static long queryLong(Connection connection, String sql, Object... params)
			throws Exception {
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			bind(statement, params);
			ResultSet results = statement.executeQuery();
			try {
				check(results.next(), "query returned no rows: " + sql);
				return results.getLong(1);
			} finally {
				results.close();
			}
		} finally {
			statement.close();
		}
	}
	
	private static String queryString(Connection connection, String sql, Object... params)
			throws Exception {
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			bind(statement, params);
			ResultSet results = statement.executeQuery();
			try {
				check(results.next(), "query returned no rows: " + sql);
				return results.getString(1);
			} finally {
				results.close();
			}
		} finally {
			statement.close();
		}
	}
	
	private static void bind(PreparedStatement statement, Object... params) throws Exception {
		for (int i = 0; i < params.length; i++) {
			statement.setObject(i + 1, params[i]);
		}
	}
	
	private static List<Statement> collect(
			TEFDialect dialect,
			byte[] bytes,
			SourceDocument source,
			ImportReport report
	) throws Exception {
		final List<Statement> statements = new ArrayList<Statement>();
		StatementConsumer consumer = new StatementConsumer() {
			@Override
			public void accept(Statement statement) {
				statements.add(statement);
			}
		};
		TEFDocumentSink sink = dialect.begin(source, consumer, report);
		TEFReader.read(source.getSourceIri(), new ByteArrayInputStream(bytes), sink);
		sink.finish(SourceProvenance.of(bytes));
		return statements;
	}
	
	
	private static boolean has(List<Statement> statements, String subject, IRI predicate, String object) {
		for (Statement statement : statements) {
			if (statement.getSubject().toString().equals(subject)
					&& statement.getPredicate().equals(predicate)
					&& statement.getObject().toString().equals(object)) {
				return true;
			}
		}
		return false;
	}
	
	private static int countObject(List<Statement> statements, IRI predicate, String object) {
		int count = 0;
		for (Statement statement : statements) {
			if (statement.getPredicate().equals(predicate)
					&& statement.getObject().toString().equals(object)) {
				count++;
			}
		}
		return count;
	}
	
	private static int countSource(List<Statement> statements, String source) {
		int count = 0;
		for (Statement statement : statements) {
			if (statement.getSource().equals(source)) {
				count++;
			}
		}
		return count;
	}
	
	private static boolean reportHas(
			ImportReport report,
			String kind,
			String entryType,
			String field
	) {
		for (ImportReport.Row row : report.getRows()) {
			if (row.getKind().equals(kind)
					&& row.getEntryType().equals(entryType)
					&& row.getField().equals(field)) {
				return true;
			}
		}
		return false;
	}
	
	private static TEFDocument readTef(String text) throws Exception {
		return readTef(text.getBytes(StandardCharsets.UTF_8));
	}
	
	private static TEFDocument readTef(byte[] bytes) throws Exception {
		return TEFReader.read(DOC, bytes);
	}
	
	private static void checkTefError(String text, String fragment) throws Exception {
		checkTefError(text.getBytes(StandardCharsets.UTF_8), fragment);
	}
	
	private static void checkTefError(byte[] bytes, String fragment) throws Exception {
		boolean threw = false;
		try {
			readTef(bytes);
		} catch (TEFReadException expected) {
			threw = true;
			check(
					expected.getMessage().contains(fragment),
					"error should mention '" + fragment + "': " + expected.getMessage());
		}
		check(threw, "syntax error should be fatal: " + fragment);
	}
	
	private static String write(Statement... statements) throws Exception {
		StringWriter out = new StringWriter();
		TOGETLTSVWriter writer = new TOGETLTSVWriter(out, AliasTable.withDefaults());
		try {
			for (Statement statement : statements) {
				writer.write(statement);
			}
		} finally {
			writer.close();
		}
		return out.toString();
	}
	
	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
	
	private static void assertEquals(Object expected, Object actual, String message) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError(
					message + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}
}
