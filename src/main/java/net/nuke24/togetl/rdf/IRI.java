package net.nuke24.togetl.rdf;

import java.util.Objects;

/**
 * An absolute or pseudo IRI. Whitespace is rejected because TOGETLTSV
 * columns are whitespace-separated.
 */
public final class IRI implements Node {
	private final String value;
	
	private IRI(String value) {
		this.value = value;
	}
	
	public static IRI of(String value) {
		if (value == null) {
			throw new IllegalArgumentException("IRI must not be null");
		}
		if (value.isEmpty()) {
			throw new IllegalArgumentException("IRI must not be empty");
		}
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				throw new IllegalArgumentException("IRI must not contain whitespace: " + value);
			}
		}
		return new IRI(value);
	}
	
	public String getValue() {
		return value;
	}
	
	@Override
	public String toString() {
		return value;
	}
	
	@Override
	public boolean equals(Object other) {
		return other instanceof IRI && value.equals(((IRI) other).value);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(value);
	}
}
