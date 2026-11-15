package net.nuke24.togetl.tef;

import java.io.IOException;

/**
 * Thrown when a TEF document contains a syntax error. The message names the location.
 */
public final class TEFReadException extends IOException {
	private static final long serialVersionUID = 1L;
	
	public TEFReadException(String message) {
		super(message);
	}
	
	public TEFReadException(String message, Throwable cause) {
		super(message, cause);
	}
}
