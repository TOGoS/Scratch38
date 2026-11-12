package net.nuke24.togetl.mapping;

/**
 * One header-to-predicate mapping rule from a mapping table.
 */
public final class FieldRule {
	private final String entryType;
	private final String header;
	private final String predicate;
	private final String valueParser;
	private final String cardinality;
	private final String asWritten;
	
	FieldRule(
			String entryType,
			String header,
			String predicate,
			String valueParser,
			String cardinality,
			String asWritten
	) {
		this.entryType = entryType;
		this.header = header;
		this.predicate = predicate;
		this.valueParser = valueParser;
		this.cardinality = cardinality;
		this.asWritten = asWritten;
	}
	
	public String getEntryType() {
		return entryType;
	}
	
	public String getHeader() {
		return header;
	}
	
	public String getPredicate() {
		return predicate;
	}
	
	public String getValueParser() {
		return valueParser;
	}
	
	public String getCardinality() {
		return cardinality;
	}
	
	public String getAsWritten() {
		return asWritten;
	}
}
