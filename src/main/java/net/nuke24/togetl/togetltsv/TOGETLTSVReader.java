package net.nuke24.togetl.togetltsv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.util.PercentCoder;

/**
 * Reads TOGETLTSV 202601 streams into statements.
 */
public final class TOGETLTSVReader {
    private TOGETLTSVReader() {
    }

    public static List<Statement> readAll(Reader in) throws IOException, TOGETLTSVParseException {
        BufferedReader reader = new BufferedReader(in);
        List<Statement> statements = new ArrayList<Statement>();
        AliasTable aliases = new AliasTable();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && line.length() > 0 && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.charAt(0) == '#') {
                handleDirective(trimmed, aliases, lineNumber);
                continue;
            }
            statements.add(parseStatement(trimmed, aliases, lineNumber));
        }
        return statements;
    }

    private static void handleDirective(
            String line,
            AliasTable aliases,
            int lineNumber
    ) throws TOGETLTSVParseException {
        String[] parts = line.split("\\s+");
        if ("#format".equals(parts[0])) {
            if (parts.length < 2) {
                throw new TOGETLTSVParseException(lineNumber, "#format requires a format IRI");
            }
            if (!TOGETLTSVWriter.FORMAT_IRI.equals(parts[1])) {
                throw new TOGETLTSVParseException(
                        lineNumber,
                        "unsupported format: " + parts[1] + " (expected " + TOGETLTSVWriter.FORMAT_IRI + ")"
                );
            }
            aliases.clear();
            return;
        }
        if ("#alias".equals(parts[0])) {
            if (parts.length != 3) {
                throw new TOGETLTSVParseException(lineNumber, "#alias requires a name and a target");
            }
            aliases.define(parts[1], aliases.expand(parts[2]));
            return;
        }
        // Comments and unknown directives are ignored.
    }

    private static Statement parseStatement(
            String line,
            AliasTable aliases,
            int lineNumber
    ) throws TOGETLTSVParseException {
        String[] columns = line.split("\\s+");
        if (columns.length != 5) {
            throw new TOGETLTSVParseException(
                    lineNumber,
                    "expected 5 columns but found " + columns.length
            );
        }
        try {
            String source = "-".equals(columns[0]) ? "-" : aliases.expand(columns[0]);
            Location location = Location.parse(columns[1]);
            Node subject = parseResource(columns[2], aliases);
            IRI predicate = IRI.of(aliases.expand(columns[3]));
            Node object = parseObject(columns[4], aliases);
            return new Statement(source, location, subject, predicate, object);
        } catch (IllegalArgumentException e) {
            throw new TOGETLTSVParseException(lineNumber, e.getMessage(), e);
        }
    }

    private static Node parseResource(String token, AliasTable aliases) {
        if (token.startsWith("_:")) {
            return BlankNode.of(token);
        }
        return IRI.of(aliases.expand(token));
    }

    private static Node parseObject(String token, AliasTable aliases) {
        if (token.startsWith("_:")) {
            return BlankNode.of(token);
        }
        if (token.startsWith("data:")) {
            int pipe = token.indexOf('|');
            String dataPart = pipe < 0 ? token : token.substring(0, pipe);
            String datatypePart = pipe < 0 ? null : token.substring(pipe + 1);
            int comma = dataPart.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("data: URI has no comma: " + token);
            }
            String value = PercentCoder.decode(dataPart.substring(comma + 1));
            IRI datatype = datatypePart == null ? null : IRI.of(aliases.expand(datatypePart));
            return Literal.of(value, datatype);
        }
        return IRI.of(aliases.expand(token));
    }
}
