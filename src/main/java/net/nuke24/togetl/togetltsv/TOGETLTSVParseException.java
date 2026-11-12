package net.nuke24.togetl.togetltsv;

/**
 * Thrown when a TOGETLTSV stream is malformed.
 */
public final class TOGETLTSVParseException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int lineNumber;

    public TOGETLTSVParseException(int lineNumber, String message) {
        super("line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public TOGETLTSVParseException(int lineNumber, String message, Throwable cause) {
        super("line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
