package net.nuke24.togetl.tef;

/**
 * Thrown when a TEF document cannot be read under the requested tolerance.
 */
public final class TEFReadException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public TEFReadException(String message) {
		super(message);
	}
	
	public TEFReadException(String message, Throwable cause) {
		super(message, cause);
	}
}
