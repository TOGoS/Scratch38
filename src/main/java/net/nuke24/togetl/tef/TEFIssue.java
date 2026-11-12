package net.nuke24.togetl.tef;

/**
 * A problem encountered while reading a TEF document.
 */
public final class TEFIssue {
    public enum Severity {
        WARNING,
        ERROR
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final TEFSpan span;
    private final String raw;

    public TEFIssue(Severity severity, String code, String message, TEFSpan span, String raw) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.span = span;
        this.raw = raw;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public TEFSpan getSpan() {
        return span;
    }

    public String getRaw() {
        return raw;
    }
}
