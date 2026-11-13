package net.nuke24.togetl.org;

/**
 * A recoverable problem found while parsing an Org document.
 */
public final class OrgDiagnostic {
	private final int line;
	private final String message;
	
	public OrgDiagnostic(int line, String message) {
		this.line = line;
		this.message = message;
	}
	
	public int getLine() {
		return line;
	}
	
	public String getMessage() {
		return message;
	}
}
