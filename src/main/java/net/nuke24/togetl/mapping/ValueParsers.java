package net.nuke24.togetl.mapping;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Value parsers named by mapping tables.
 */
public final class ValueParsers {
    private static final Map<String, ValueParser> PARSERS = new LinkedHashMap<String, ValueParser>();

    static {
        PARSERS.put("plain", new PlainParser());
        PARSERS.put("iri", new IriParser());
        PARSERS.put("fileRef", new FileRefParser());
        PARSERS.put("csv", new CsvParser());
        PARSERS.put("csvPartRef", new CsvPartRefParser());
        PARSERS.put("partRef", new PartRefParser());
        PARSERS.put("number", new NumberParser());
        PARSERS.put("timestamp", new TimestampParser());
        PARSERS.put("bool", new BoolParser());
        PARSERS.put("x-rdf-subject", new XRdfSubjectParser());
    }

    private ValueParsers() {
    }

    public static ValueParser byName(String name) {
        ValueParser parser = PARSERS.get(name);
        if (parser == null) {
            throw new IllegalArgumentException(
                    "Unknown value parser: " + name + " (known: " + PARSERS.keySet() + ")");
        }
        return parser;
    }

    public static boolean isKnown(String name) {
        return PARSERS.containsKey(name);
    }

    /**
     * Only well-known URI schemes become IRIs. A value such as
     * {@code ttp://example/} is kept as written and reported instead of being
     * silently corrected.
     */
    public static boolean isAcceptableIri(String value) {
        if (value == null || value.isEmpty() || containsWhitespace(value)) {
            return false;
        }
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String scheme = value.substring(0, colon).toLowerCase(Locale.ROOT);
        return "http".equals(scheme)
                || "https".equals(scheme)
                || "urn".equals(scheme)
                || "tag".equals(scheme)
                || "file".equals(scheme)
                || "ftp".equals(scheme)
                || "mailto".equals(scheme)
                || "data".equals(scheme)
                || "x-git-object".equals(scheme)
                || "doke-entry".equals(scheme);
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitCsv(String value) {
        String[] raw = value.split(",");
        int count = 0;
        String[] trimmed = new String[raw.length];
        for (String item : raw) {
            String candidate = item.trim();
            if (!candidate.isEmpty()) {
                trimmed[count++] = candidate;
            }
        }
        if (count == raw.length) {
            return trimmed;
        }
        String[] result = new String[count];
        System.arraycopy(trimmed, 0, result, 0, count);
        return result;
    }

    private static void emitIriOrAsWritten(ValueParserContext context, String value) throws IOException {
        if (isAcceptableIri(value)) {
            context.emit(IRI.of(value));
        } else {
            context.report("invalid-iri");
            context.emit(Literal.of(value));
            context.emitRaw(context.getSubject(), Vocab.TOGETL_AS_WRITTEN, Literal.of(value));
        }
    }

    private static void emitPartRef(ValueParserContext context, String value) throws IOException {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            context.report("empty-value");
            return;
        }
        if (isAcceptableIri(trimmed)) {
            context.emit(IRI.of(trimmed));
        } else {
            context.emit(IRI.of(Identifiers.referenceIri(trimmed)));
        }
    }

    private static final class PlainParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            context.emit(Literal.of(context.getHeader().getValue()));
        }
    }

    private static final class IriParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            emitIriOrAsWritten(context, context.getHeader().getValue().trim());
        }
    }

    private static final class FileRefParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            FileRef ref = parseFileRef(context.getHeader().getValue());
            Node object;
            if (ref.uri != null && isAcceptableIri(ref.uri)) {
                object = IRI.of(ref.uri);
            } else {
                object = context.blankNode("file", 0);
            }
            context.emit(object);
            if (ref.name != null && !ref.name.isEmpty()) {
                context.emitRaw(object, Vocab.TOGETL_FILENAME, Literal.of(ref.name));
            }
            context.emitRaw(object, Vocab.RDF_TYPE, Vocab.TOGETL_FILE);
            if (ref.uri != null && !isAcceptableIri(ref.uri)) {
                context.report("invalid-iri");
                context.emitRaw(context.getSubject(), Vocab.TOGETL_AS_WRITTEN, Literal.of(ref.uri));
            }
        }
    }

    private static final class CsvParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            String[] values = splitCsv(context.getHeader().getValue());
            if (values.length == 0) {
                context.emit(Literal.of(""));
                return;
            }
            for (String value : values) {
                context.emit(Literal.of(value));
            }
        }
    }

    private static final class CsvPartRefParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            for (String value : splitCsv(context.getHeader().getValue())) {
                emitPartRef(context, value);
            }
        }
    }

    private static final class PartRefParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            emitPartRef(context, context.getHeader().getValue());
        }
    }

    private static final class NumberParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            String value = context.getHeader().getValue().trim();
            if (value.matches("-?\\d+")) {
                context.emit(Literal.of(value, Vocab.XSD_INTEGER));
            } else if (value.matches("-?\\d+\\.\\d+")) {
                context.emit(Literal.of(value, IRI.of(Vocab.XSD_NS + "decimal")));
            } else {
                context.report("unparsed-value");
                context.emit(Literal.of(value));
            }
        }
    }

    private static final class TimestampParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            String value = context.getHeader().getValue().trim();
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                context.emit(Literal.of(value, IRI.of(Vocab.XSD_NS + "date")));
            } else if (value.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                context.emit(Literal.of(value, IRI.of(Vocab.XSD_NS + "dateTime")));
            } else {
                context.report("unparsed-value");
                context.emit(Literal.of(value));
            }
        }
    }

    private static final class BoolParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            String value = context.getHeader().getValue().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
                context.emit(Literal.of("true", Vocab.XSD_BOOLEAN));
            } else if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
                context.emit(Literal.of("false", Vocab.XSD_BOOLEAN));
            } else {
                context.report("unparsed-value");
                context.emit(Literal.of(context.getHeader().getValue()));
            }
        }
    }

    private static final class XRdfSubjectParser implements ValueParser {
        @Override
        public void parse(ValueParserContext context) throws IOException {
            String value = context.getHeader().getValue().trim();
            String prefix = "x-rdf-subject:";
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
            }
            emitIriOrAsWritten(context, value);
        }
    }

    private static FileRef parseFileRef(String raw) {
        int tab = raw.indexOf('\t');
        if (tab >= 0) {
            return new FileRef(raw.substring(0, tab).trim(), raw.substring(tab + 1).trim());
        }
        String trimmed = raw.trim();
        int space = firstWhitespace(trimmed);
        if (space > 0) {
            String first = trimmed.substring(0, space).trim();
            String second = trimmed.substring(space + 1).trim();
            if (looksLikeUri(second) && !looksLikeUri(first)) {
                return new FileRef(first, second);
            }
            if (looksLikeUri(first)) {
                return new FileRef(null, first);
            }
        }
        if (looksLikeUri(trimmed)) {
            return new FileRef(null, trimmed);
        }
        return new FileRef(trimmed.isEmpty() ? null : trimmed, null);
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean looksLikeUri(String value) {
        int colon = value.indexOf(':');
        return colon > 0 && !containsWhitespace(value);
    }

    private static final class FileRef {
        final String name;
        final String uri;

        FileRef(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }
    }
}
