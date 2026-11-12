package net.nuke24.togetl.rdf;

import java.util.Objects;

import net.nuke24.togetl.util.PercentCoder;

/**
 * A literal value, optionally with a datatype IRI.
 *
 * <p>Serialized in TOGETLTSV as a data: URI containing percent-encoded
 * UTF-8, with the datatype appended after a pipe when present.</p>
 */
public final class Literal implements Node {
	private final String value;
	private final IRI datatype;
	
	private Literal(String value, IRI datatype) {
		this.value = value;
		this.datatype = datatype;
	}
	
	public static Literal of(String value) {
		return of(value, null);
	}
	
	public static Literal of(String value, IRI datatype) {
		if (value == null) {
			throw new IllegalArgumentException("Literal value must not be null");
		}
		return new Literal(value, datatype);
	}
	
	public String getValue() {
		return value;
	}
	
	public IRI getDatatype() {
		return datatype;
	}
	
	public String getDataUri() {
		return "data:," + PercentCoder.encode(value);
	}
	
	@Override
	public String toString() {
		return datatype == null ? getDataUri() : getDataUri() + "|" + datatype;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Literal)) return false;
		Literal that = (Literal) other;
		return value.equals(that.value) && Objects.equals(datatype, that.datatype);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(value, datatype);
	}
}
