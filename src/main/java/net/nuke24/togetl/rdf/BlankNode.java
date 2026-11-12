package net.nuke24.togetl.rdf;

import java.util.Objects;

/**
 * A blank node, serialized as "_:" + label.
 */
public final class BlankNode implements Node {
	private final String label;
	
	private BlankNode(String label) {
		this.label = label;
	}
	
	public static BlankNode of(String label) {
		if (label == null) {
			throw new IllegalArgumentException("Blank node label must not be null");
		}
		if (label.startsWith("_:")) {
			label = label.substring(2);
		}
		if (label.isEmpty()) {
			throw new IllegalArgumentException("Blank node label must not be empty");
		}
		for (int i = 0; i < label.length(); i++) {
			if (Character.isWhitespace(label.charAt(i))) {
				throw new IllegalArgumentException("Blank node label must not contain whitespace: " + label);
			}
		}
		return new BlankNode(label);
	}
	
	public String getLabel() {
		return label;
	}
	
	@Override
	public String toString() {
		return "_:" + label;
	}
	
	@Override
	public boolean equals(Object other) {
		return other instanceof BlankNode && label.equals(((BlankNode) other).label);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(label);
	}
}
