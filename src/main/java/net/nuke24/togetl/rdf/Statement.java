package net.nuke24.togetl.rdf;

import java.util.Objects;

/**
 * One statement: "$subject $predicate $object, according to $source at $location".
 */
public final class Statement {
	private final String source;
	private final Location location;
	private final Node subject;
	private final IRI predicate;
	private final Node object;
	
	public Statement(String source, Location location, Node subject, IRI predicate, Node object) {
		if (source == null || source.isEmpty()) {
			throw new IllegalArgumentException("Statement source must not be empty");
		}
		if (!"-".equals(source)) {
			checkNoWhitespace(source, "source");
		}
		if (location == null) {
			throw new IllegalArgumentException("Statement location must not be null");
		}
		if (!(subject instanceof IRI) && !(subject instanceof BlankNode)) {
			throw new IllegalArgumentException("Statement subject must be an IRI or blank node");
		}
		if (predicate == null) {
			throw new IllegalArgumentException("Statement predicate must not be null");
		}
		if (object == null) {
			throw new IllegalArgumentException("Statement object must not be null");
		}
		this.source = source;
		this.location = location;
		this.subject = subject;
		this.predicate = predicate;
		this.object = object;
	}
	
	private static void checkNoWhitespace(String value, String what) {
		for (int i = 0; i < value.length(); i++) {
			if (Character.isWhitespace(value.charAt(i))) {
				throw new IllegalArgumentException("Statement " + what + " must not contain whitespace: " + value);
			}
		}
	}
	
	public String getSource() {
		return source;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public Node getSubject() {
		return subject;
	}
	
	public IRI getPredicate() {
		return predicate;
	}
	
	public Node getObject() {
		return object;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Statement)) return false;
		Statement that = (Statement) other;
		return source.equals(that.source)
				&& location.equals(that.location)
				&& subject.equals(that.subject)
				&& predicate.equals(that.predicate)
				&& object.equals(that.object);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(source, location, subject, predicate, object);
	}
	
	@Override
	public String toString() {
		return source + " " + location + " " + subject + " " + predicate + " " + object;
	}
}
