package net.nuke24.togetl.org;

/**
 * An unclosed block or drawer. Org parsing stops at the first one, and the
 * message names the line where the construct was opened.
 */
public final class OrgSyntaxException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final int line;

	public OrgSyntaxException(int line, String message) {
		super("line " + line + ": " + message);
		this.line = line;
	}

	public int getLine() {
		return line;
	}
}
