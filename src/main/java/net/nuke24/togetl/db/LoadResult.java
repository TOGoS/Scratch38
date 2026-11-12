package net.nuke24.togetl.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Summary of one load operation.
 */
public final class LoadResult {
    private final String batchIri;
    private final List<String> documentIris;
    private final int statementCount;
    private final int parseIssueCount;
    private final int reportRowCount;
    private final int vocabTermCount;
    private final boolean replacedBatch;

    LoadResult(
            String batchIri,
            List<String> documentIris,
            int statementCount,
            int parseIssueCount,
            int reportRowCount,
            int vocabTermCount,
            boolean replacedBatch
    ) {
        this.batchIri = batchIri;
        this.documentIris = Collections.unmodifiableList(new ArrayList<String>(documentIris));
        this.statementCount = statementCount;
        this.parseIssueCount = parseIssueCount;
        this.reportRowCount = reportRowCount;
        this.vocabTermCount = vocabTermCount;
        this.replacedBatch = replacedBatch;
    }

    public String getBatchIri() {
        return batchIri;
    }

    public List<String> getDocumentIris() {
        return documentIris;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public int getParseIssueCount() {
        return parseIssueCount;
    }

    public int getReportRowCount() {
        return reportRowCount;
    }

    public int getVocabTermCount() {
        return vocabTermCount;
    }

    public boolean isReplacedBatch() {
        return replacedBatch;
    }
}
