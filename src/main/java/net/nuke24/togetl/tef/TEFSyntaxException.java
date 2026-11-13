package net.nuke24.togetl.tef;

/**
 * A syntax error found while lexing or assembling a TEF document. It is
 * thrown inside the streaming pipeline and reported by {@link TEFReader} as a
 * {@link TEFReadException} that names the location.
 */
final class TEFSyntaxException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final TEFSpan span;

	TEFSyntaxException(String message, TEFSpan span) {
		super(message);
		this.span = span;
	}

	TEFSpan getSpan() {
		return span;
	}
}
