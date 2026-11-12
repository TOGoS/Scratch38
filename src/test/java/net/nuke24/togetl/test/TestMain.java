package net.nuke24.togetl.test;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.nuke24.togetl.cli.ImportCommand;
import net.nuke24.togetl.dialect.GenericTEFDialect;
import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.tef.TEFIssue;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.tef.TEFReader;
import net.nuke24.togetl.togetltsv.AliasTable;
import net.nuke24.togetl.togetltsv.TOGETLTSVReader;
import net.nuke24.togetl.togetltsv.TOGETLTSVWriter;
import net.nuke24.togetl.util.PercentCoder;
import net.nuke24.togetl.util.Tolerance;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Minimal test runner. Keeps the build dependency-free.
 */
public final class TestMain {
    private static final String DOC = "tag:nuke24.net,2026-11:togetl:document:abc";
    private static final String PART = "tag:nuke24.net,2026-11:togetl:part:p1310";
    private static final String TOGETL = "http://ns.nuke24.net/TOGETL/";
    private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

    private static int failures = 0;
    private static int passed = 0;

    private interface Check {
        void run() throws Exception;
    }

    private TestMain() {
    }

    public static void main(String[] args) {
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
        run("identifiers", TestMain::testIdentifiers);
        run("generic dialect", TestMain::testGenericDialect);
        run("generic dialect round trip", TestMain::testGenericDialectRoundTrip);
        run("import command", TestMain::testImportCommand);

        System.out.println();
        if (failures > 0) {
            System.out.println(failures + " test(s) failed, " + passed + " passed");
            System.exit(1);
        }
        System.out.println(passed + " test(s) passed");
    }

    private static void run(String name, Check check) {
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
                + "#alias togetl: http://ns.nuke24.net/TOGETL/\n"
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
        assertEquals(0, document.getIssues().size(), "issue count");
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
        assertEquals(0, document.getIssues().size(), "issue count");
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
        assertEquals(0, document.getIssues().size(), "issue count");
        assertEquals(2, document.getEntries().size(), "entry count");
        assertEquals("hello", document.getEntries().get(0).getContent(), "first content");
        assertEquals("world", document.getEntries().get(1).getContent(), "second content");
    }

    private static void testTefEqualsEscape() throws Exception {
        TEFDocument document = readTef("=foo bar\n\nhello\n==\n");
        assertEquals(0, document.getIssues().size(), "issue count");
        assertEquals(1, document.getEntries().size(), "entry count");
        assertEquals("hello\n=\n", document.getEntries().get(0).getContent(), "escaped equals");
    }

    private static void testTefEscapedHeaderEquals() throws Exception {
        TEFDocument document = readTef("=foo bar\n==note: value\n\ncontent");
        assertEquals(0, document.getIssues().size(), "issue count");
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
        assertEquals(0, document.getIssues().size(), "issue count");
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
        String text = "=part p1\n name: Bob\n";
        TEFDocument document = readTef(text);
        assertEquals(1, document.getIssues().size(), "issue count");
        assertEquals("orphan-continuation", document.getIssues().get(0).getCode(), "issue code");
        assertEquals(1, document.getEntries().size(), "entry count");

        TEFEntry entry = document.getEntries().get(0);
        assertEquals(0, entry.getHeaders().size(), "header count");
        assertEquals(" name: Bob", entry.getContent(), "recovered content");

        checkThrowsStrict(text);
    }

    private static void testTefHeaderWithoutColon() throws Exception {
        String text = "=part p1\nname: Bob\nThis is content.\n\nmore";
        TEFDocument document = readTef(text);
        assertEquals(1, document.getIssues().size(), "issue count");
        assertEquals("header-without-colon", document.getIssues().get(0).getCode(), "issue code");

        TEFEntry entry = document.getEntries().get(0);
        assertEquals(1, entry.getHeaders().size(), "header count");
        assertEquals("This is content.\nmore", entry.getContent(), "recovered content");

        checkThrowsStrict(text);
    }

    private static void testTefTightHeader() throws Exception {
        String text =
                "=recording x\n"
                + "x-rdf-subject:urn:bitprint:ABC\n"
                + "title: Bob\n";
        TEFDocument document = readTef(text);
        assertEquals(1, document.getIssues().size(), "issue count");
        assertEquals("tight-header-colon", document.getIssues().get(0).getCode(), "issue code");

        TEFEntry entry = document.getEntries().get(0);
        assertEquals(2, entry.getHeaders().size(), "header count");
        assertEquals("x-rdf-subject", entry.getHeaders().get(0).getKey(), "tight header key");
        assertEquals("urn:bitprint:ABC", entry.getHeaders().get(0).getValue(), "tight header value");
        assertEquals("title", entry.getHeaders().get(1).getKey(), "following header key");
        assertEquals("Bob", entry.getHeaders().get(1).getValue(), "following header value");

        checkThrowsStrict(text);
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

        TEFDocument document = readTef(bytes, Tolerance.WARN);
        assertEquals(1, document.getIssues().size(), "issue count");
        assertEquals("invalid-utf8", document.getIssues().get(0).getCode(), "issue code");
        assertEquals(1, document.getEntries().size(), "entry count");

        TEFHeader header = document.getEntries().get(0).getHeaders().get(0);
        assertEquals("name", header.getKey(), "header key");
        check(header.getValue().indexOf('\uFFFD') >= 0, "replacement character should be present");

        boolean threw = false;
        try {
            readTef(bytes, Tolerance.STRICT);
        } catch (TEFReadException expected) {
            threw = true;
        }
        check(threw, "strict should reject invalid UTF-8");
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
        assertEquals(0, document.getIssues().size(), "issue count");
        assertEquals(1, document.getEntries().size(), "entry count");

        TEFEntry entry = document.getEntries().get(0);
        assertEquals("Bob", entry.getHeaders().get(0).getValue(), "header value");
        assertEquals("content\r\n", entry.getContent(), "content preserves CRLF");
    }

    private static void testIdentifiers() throws Exception {
        String url = "https://example.com/a b.tef";
        String documentIri = Identifiers.documentIri(url);
        String prefix = Identifiers.TAG_PREFIX + "document:";
        check(documentIri.startsWith(prefix), "document IRI prefix: " + documentIri);
        assertEquals(prefix.length() + 16, documentIri.length(), "document hash length");
        assertEquals(documentIri, Identifiers.documentIri(url), "document IRI is deterministic");
        check(!documentIri.equals(Identifiers.documentIri(url + "x")), "different URLs differ");

        String entryIri = Identifiers.tefEntryIri(documentIri, 7);
        check(entryIri.startsWith(Identifiers.TAG_PREFIX + "tef-entry:"), "entry IRI prefix");
        check(entryIri.endsWith(":7"), "entry ordinal suffix");

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
        TEFDocument document = TEFReader.read(sourceUrl, bytes);
        SourceDocument source = SourceDocument.of(sourceUrl, bytes, "tef", "test/1");
        ImportReport report = new ImportReport();

        List<Statement> statements = collect(new GenericTEFDialect(), document, source, report);
        String doc = source.getIri();
        String entry = Identifiers.tefEntryIri(doc, 0);

        check(has(statements, doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT.toString()),
                "document type statement");
        check(has(statements, doc, Vocab.TOGETL_SHA256, Literal.of(Identifiers.sha256(bytes)).toString()),
                "document hash statement");
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
        TEFDocument document = TEFReader.read(sourceUrl, bytes);
        SourceDocument source = SourceDocument.of(sourceUrl, bytes, "tef", "test/1");
        ImportReport report = new ImportReport();

        List<Statement> statements = collect(new GenericTEFDialect(), document, source, report);
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
                "--report", report.toString()
        }), "first import exit code");

        assertEquals(0, ImportCommand.run(new String[] {
                "--input", input.toString(),
                "--output", second.toString()
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

    private static List<Statement> collect(
            GenericTEFDialect dialect,
            TEFDocument document,
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
        dialect.map(document, source, consumer, report);
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
        return readTef(text.getBytes(StandardCharsets.UTF_8), Tolerance.WARN);
    }

    private static TEFDocument readTef(byte[] bytes, Tolerance tolerance) throws Exception {
        return TEFReader.read(DOC, bytes, tolerance);
    }

    private static void checkThrowsStrict(String text) throws Exception {
        boolean threw = false;
        try {
            readTef(text.getBytes(StandardCharsets.UTF_8), Tolerance.STRICT);
        } catch (TEFReadException expected) {
            threw = true;
        }
        check(threw, "strict should reject: " + text);
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
