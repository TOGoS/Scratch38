package net.nuke24.togetl.vocab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A registry of vocabulary terms collected from TEF vocabulary documents.
 *
 * <p>A term may be defined more than once. Duplicates are retained in
 * {@link Term#getDefinedIn()} and exposed through {@link #getDuplicateIris()} so
 * the report can point them out.</p>
 */
public final class Vocabulary {
    private final Map<String, Term> terms = new LinkedHashMap<String, Term>();
    private final Set<String> duplicateIris = new LinkedHashSet<String>();

    public Term define(
            String iri,
            String kind,
            String label,
            String comment,
            String status,
            String definedIn
    ) {
        if (iri == null || iri.isEmpty()) {
            throw new IllegalArgumentException("Term IRI must not be empty");
        }
        Term term = terms.get(iri);
        if (term == null) {
            term = new Term(iri, kind, label, comment, status);
            terms.put(iri, term);
        } else {
            duplicateIris.add(iri);
        }
        term.definedIn.add(definedIn);
        return term;
    }

    public Term get(String iri) {
        return terms.get(iri);
    }

    public boolean isDefined(String iri) {
        return terms.containsKey(iri);
    }

    public Collection<Term> getTerms() {
        return Collections.unmodifiableCollection(new ArrayList<Term>(terms.values()));
    }

    public Set<String> getDuplicateIris() {
        return Collections.unmodifiableSet(duplicateIris);
    }

    public int size() {
        return terms.size();
    }

    public static final class Term {
        private final String iri;
        private final String kind;
        private final String label;
        private final String comment;
        private final String status;
        private final List<String> definedIn = new ArrayList<String>();

        Term(String iri, String kind, String label, String comment, String status) {
            this.iri = iri;
            this.kind = kind;
            this.label = label;
            this.comment = comment;
            this.status = status;
        }

        public String getIri() {
            return iri;
        }

        public String getKind() {
            return kind;
        }

        public String getLabel() {
            return label;
        }

        public String getComment() {
            return comment;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getDefinedIn() {
            return Collections.unmodifiableList(definedIn);
        }
    }
}
