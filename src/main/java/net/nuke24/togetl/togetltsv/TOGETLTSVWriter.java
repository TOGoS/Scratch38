package net.nuke24.togetl.togetltsv;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Node;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.util.PercentCoder;

/**
 * Writes TOGETLTSV 202601 streams.
 *
 * <p>Each writer instance emits a self-contained stream: the format line,
 * an alias block, and then statements. Concatenating streams is safe because
 * the next format line clears the alias table.</p>
 */
public final class TOGETLTSVWriter implements Closeable {
    public static final String FORMAT_IRI = "urn:uuid:b783bac7-58e9-4340-93ef-7973914732d5";

    private final Writer out;
    private final AliasTable aliases;

    public TOGETLTSVWriter(Writer out, AliasTable aliases) throws IOException {
        if (out == null) throw new IllegalArgumentException("out must not be null");
        if (aliases == null) throw new IllegalArgumentException("aliases must not be null");
        this.out = out;
        this.aliases = aliases;
        writeHeader();
    }

    private void writeHeader() throws IOException {
        out.write("#format " + FORMAT_IRI + "\n");
        for (Map.Entry<String, String> entry : aliases.getAliases().entrySet()) {
            out.write("#alias " + entry.getKey() + " " + entry.getValue() + "\n");
        }
        out.write("\n");
    }

    public void write(Statement statement) throws IOException {
        out.write(formatSource(statement.getSource()));
        out.write(' ');
        out.write(statement.getLocation().toString());
        out.write(' ');
        out.write(formatResource(statement.getSubject()));
        out.write(' ');
        out.write(aliases.compress(statement.getPredicate().getValue()));
        out.write(' ');
        out.write(formatObject(statement.getObject()));
        out.write('\n');
    }

    private String formatSource(String source) {
        return "-".equals(source) ? source : aliases.compress(source);
    }

    private String formatResource(Node node) {
        if (node instanceof IRI) {
            return aliases.compress(((IRI) node).getValue());
        }
        if (node instanceof BlankNode) {
            return node.toString();
        }
        throw new IllegalArgumentException("Subject must be an IRI or blank node: " + node);
    }

    private String formatObject(Node node) {
        if (node instanceof IRI) {
            return aliases.compress(((IRI) node).getValue());
        }
        if (node instanceof BlankNode) {
            return node.toString();
        }
        if (node instanceof Literal) {
            Literal literal = (Literal) node;
            StringBuilder out = new StringBuilder(literal.getDataUri());
            if (literal.getDatatype() != null) {
                out.append('|');
                out.append(aliases.compress(literal.getDatatype().getValue()));
            }
            return out.toString();
        }
        throw new IllegalArgumentException("Unsupported object node: " + node);
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
