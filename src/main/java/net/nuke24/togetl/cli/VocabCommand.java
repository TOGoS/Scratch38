package net.nuke24.togetl.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.nuke24.togetl.mapping.MappingTable;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Statement;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.togetltsv.TOGETLTSVParseException;
import net.nuke24.togetl.togetltsv.TOGETLTSVReader;
import net.nuke24.togetl.vocab.Vocab;
import net.nuke24.togetl.vocab.Vocabulary;
import net.nuke24.togetl.vocab.VocabularyLoader;
import net.nuke24.togetl.vocab.VocabularyReport;

/**
 * {@code togetl vocab}: check mapping terms against vocabulary definitions.
 */
public final class VocabCommand {
    private final List<String> mappings = new ArrayList<String>();
    private final List<String> vocabFiles = new ArrayList<String>();
    private final List<String> inputs = new ArrayList<String>();
    private String reportPath;

    private VocabCommand() {
    }

    public static int run(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                usage(System.out);
                return 0;
            }
        }
        VocabCommand command = new VocabCommand();
        try {
            command.parse(args);
            return command.execute();
        } catch (UsageException e) {
            System.err.println("togetl vocab: " + e.getMessage());
            usage(System.err);
            return 1;
        } catch (IOException e) {
            System.err.println("togetl vocab: " + e.getMessage());
            return 1;
        } catch (TOGETLTSVParseException e) {
            System.err.println("togetl vocab: " + e.getMessage());
            return 1;
        } catch (TEFReadException e) {
            System.err.println("togetl vocab: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println("togetl vocab: " + e.getMessage());
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
            if ("--mapping".equals(key)) {
                mappings.add(value != null ? value : next(args, ++i, key));
            } else if ("--vocab".equals(key)) {
                vocabFiles.add(value != null ? value : next(args, ++i, key));
            } else if ("--input".equals(key)) {
                inputs.add(value != null ? value : next(args, ++i, key));
            } else if ("--report".equals(key)) {
                reportPath = value != null ? value : next(args, ++i, key);
            } else {
                throw new UsageException("Unknown option: " + arg);
            }
        }
    }

    private static String next(String[] args, int index, String option) throws UsageException {
        if (index >= args.length) {
            throw new UsageException("Missing value for " + option);
        }
        return args[index];
    }

    private int execute() throws IOException, TOGETLTSVParseException, TEFReadException {
        List<MappingTable> tables = new ArrayList<MappingTable>();
        if (mappings.isEmpty()) {
            for (Path path : MappingTable.defaultFiles()) {
                tables.add(MappingTable.load(path.toString()));
            }
        } else {
            for (String mapping : mappings) {
                tables.add(MappingTable.load(mapping));
            }
        }

        Vocabulary vocabulary = new Vocabulary();
        if (vocabFiles.isEmpty()) {
            for (Path path : VocabularyLoader.defaultVocabFiles()) {
                VocabularyLoader.loadInto(vocabulary, path);
            }
        } else {
            for (String vocabFile : vocabFiles) {
                VocabularyLoader.loadInto(vocabulary, Paths.get(vocabFile));
            }
        }

        Map<String, Set<String>> extraUses = new TreeMap<String, Set<String>>();
        for (String input : inputs) {
            collectUses(Paths.get(input), extraUses);
        }

        VocabularyReport report = VocabularyReport.analyze(vocabulary, tables, extraUses);
        if (reportPath == null || "-".equals(reportPath)) {
            Writer out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
            report.writeTsv(out);
            out.flush();
        } else {
            Path target = Paths.get(reportPath);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Writer out = new OutputStreamWriter(
                    Files.newOutputStream(target), StandardCharsets.UTF_8);
            try {
                report.writeTsv(out);
            } finally {
                out.close();
            }
        }

        System.err.println("Vocabulary terms: " + vocabulary.size()
                + ", mappings: " + tables.size()
                + ", rows: " + report.getRows().size());
        System.err.println("  undefined " + report.count(VocabularyReport.UNDEFINED)
                + ", proposed " + report.count(VocabularyReport.PROPOSED)
                + ", unused " + report.count(VocabularyReport.UNUSED)
                + ", duplicate " + report.count(VocabularyReport.DUPLICATE));
        if (report.hasUndefined()) {
            System.err.println("togetl vocab: mapping references undefined terms");
            return 1;
        }
        return 0;
    }

    private static void collectUses(Path path, Map<String, Set<String>> uses)
            throws IOException, TOGETLTSVParseException {
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        List<Statement> statements;
        try {
            statements = TOGETLTSVReader.readAll(reader);
        } finally {
            reader.close();
        }
        String source = path.getFileName().toString();
        for (Statement statement : statements) {
            addUse(uses, statement.getPredicate().toString(), source);
            if (Vocab.RDF_TYPE.equals(statement.getPredicate())
                    && statement.getObject() instanceof IRI) {
                addUse(uses, statement.getObject().toString(), source);
            }
        }
    }

    private static void addUse(Map<String, Set<String>> uses, String iri, String source) {
        Set<String> sources = uses.get(iri);
        if (sources == null) {
            sources = new TreeSet<String>();
            uses.put(iri, sources);
        }
        sources.add(source);
    }

    public static void usage(java.io.PrintStream err) {
        err.println("Usage: togetl vocab [options]");
        err.println();
        err.println("  --mapping <name|file>  Mapping table (default: all mappings/*.tsv)");
        err.println("  --vocab <file>         Vocabulary TEF file (default: all vocab/*.tef)");
        err.println("  --input <file>         TOGETLTSV stream whose terms are also checked");
        err.println("  --report <file|->      Write the report as TSV (default: -)");
        err.println();
        err.println("Exits non-zero when a mapping references an undefined term.");
    }

    private static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
