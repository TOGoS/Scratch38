package net.nuke24.togetl.vocab;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.nuke24.togetl.mapping.EntryRule;
import net.nuke24.togetl.mapping.FieldRule;
import net.nuke24.togetl.mapping.MappingTable;

/**
 * Compares vocabulary definitions with the terms used by mapping tables.
 */
public final class VocabularyReport {
    public static final String OK = "ok";
    public static final String UNDEFINED = "undefined";
    public static final String PROPOSED = "proposed";
    public static final String UNUSED = "unused";
    public static final String DUPLICATE = "duplicate";
    public static final String DEPRECATED = "deprecated";
    public static final String DEPRECATED_IN_USE = "deprecated-in-use";

    private final List<Row> rows;

    private VocabularyReport(List<Row> rows) {
        this.rows = Collections.unmodifiableList(rows);
    }

    public static VocabularyReport analyze(Vocabulary vocabulary, List<MappingTable> tables) {
        return analyze(vocabulary, tables, Collections.<String, Set<String>>emptyMap());
    }

    public static VocabularyReport analyze(
            Vocabulary vocabulary,
            List<MappingTable> tables,
            Map<String, Set<String>> extraUses
    ) {
        Map<String, Set<String>> usedBy = new TreeMap<String, Set<String>>();
        for (MappingTable table : tables) {
            for (EntryRule rule : table.getEntryRules()) {
                if (rule.getTypeIri() != null) {
                    addUse(usedBy, rule.getTypeIri(), table.getName());
                }
            }
            for (FieldRule rule : table.getFieldRules()) {
                addUse(usedBy, rule.getPredicate(), table.getName());
            }
        }
        for (Map.Entry<String, Set<String>> entry : extraUses.entrySet()) {
            for (String source : entry.getValue()) {
                addUse(usedBy, entry.getKey(), source);
            }
        }
        addUse(usedBy, Vocab.RDF_TYPE.toString(), "dialect");
        addUse(usedBy, Vocab.RDFS_COMMENT.toString(), "dialect");
        addUse(usedBy, Vocab.TOGETL_DOCUMENT_LINK.toString(), "dialect");
        addUse(usedBy, Vocab.TEF_ENTRY_INDEX.toString(), "dialect");
        addUse(usedBy, Vocab.TEF_ENTRY_TYPE.toString(), "dialect");
        addUse(usedBy, Vocab.TOGETL_AS_WRITTEN.toString(), "dialect");
        addUse(usedBy, Vocab.TOGETL_FILENAME.toString(), "dialect");
        addUse(usedBy, Vocab.TOGETL_FILE.toString(), "dialect");

        Set<String> allTerms = new TreeSet<String>();
        allTerms.addAll(usedBy.keySet());
        for (Vocabulary.Term term : vocabulary.getTerms()) {
            allTerms.add(term.getIri());
        }

        List<Row> rows = new ArrayList<Row>();
        for (String iri : allTerms) {
            Vocabulary.Term term = vocabulary.get(iri);
            boolean used = usedBy.containsKey(iri);
            boolean defined = term != null;
            boolean duplicate = vocabulary.getDuplicateIris().contains(iri);
            String status;
            String note = "";
            if (duplicate) {
                status = DUPLICATE;
                note = "defined " + term.getDefinedIn().size() + " times";
            } else if (used && !defined) {
                status = iri.startsWith(Vocab.TOGETL_NS) ? PROPOSED : UNDEFINED;
            } else if (defined && !used) {
                status = UNUSED;
            } else if (defined && "deprecated".equals(term.getStatus())) {
                status = used ? DEPRECATED_IN_USE : DEPRECATED;
            } else {
                status = OK;
            }
            if (term != null && term.getKind() != null && note.isEmpty()) {
                note = term.getKind();
            }
            rows.add(new Row(
                    iri,
                    status,
                    join(usedBy.get(iri)),
                    term == null ? "" : join(term.getDefinedIn()),
                    note));
        }
        return new VocabularyReport(rows);
    }

    private static void addUse(Map<String, Set<String>> usedBy, String iri, String source) {
        Set<String> sources = usedBy.get(iri);
        if (sources == null) {
            sources = new TreeSet<String>();
            usedBy.put(iri, sources);
        }
        sources.add(source);
    }

    private static String join(Iterable<String> values) {
        if (values == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(value);
        }
        return out.toString();
    }

    public List<Row> getRows() {
        return rows;
    }

    public boolean hasUndefined() {
        for (Row row : rows) {
            if (UNDEFINED.equals(row.status) || PROPOSED.equals(row.status)) {
                return true;
            }
        }
        return false;
    }

    public int count(String status) {
        int count = 0;
        for (Row row : rows) {
            if (status.equals(row.status)) {
                count++;
            }
        }
        return count;
    }

    public void writeTsv(Writer out) throws IOException {
        out.write("term\tstatus\tusedBy\tdefinedBy\tnote\n");
        for (Row row : rows) {
            out.write(clean(row.term));
            out.write('\t');
            out.write(row.status);
            out.write('\t');
            out.write(clean(row.usedBy));
            out.write('\t');
            out.write(clean(row.definedBy));
            out.write('\t');
            out.write(clean(row.note));
            out.write('\n');
        }
    }

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    public static final class Row {
        private final String term;
        private final String status;
        private final String usedBy;
        private final String definedBy;
        private final String note;

        Row(String term, String status, String usedBy, String definedBy, String note) {
            this.term = term;
            this.status = status;
            this.usedBy = usedBy;
            this.definedBy = definedBy;
            this.note = note;
        }

        public String getTerm() {
            return term;
        }

        public String getStatus() {
            return status;
        }

        public String getUsedBy() {
            return usedBy;
        }

        public String getDefinedBy() {
            return definedBy;
        }

        public String getNote() {
            return note;
        }
    }
}
