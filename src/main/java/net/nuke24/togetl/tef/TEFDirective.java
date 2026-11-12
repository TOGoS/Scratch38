package net.nuke24.togetl.tef;

/**
 * A SharpFormat directive such as "#format" or "#alias".
 */
public final class TEFDirective {
	private final String name;
	private final String value;
	private final String raw;
	private final TEFSpan span;
	
	public TEFDirective(String name, String value, String raw, TEFSpan span) {
		this.name = name;
		this.value = value;
		this.raw = raw;
		this.span = span;
	}
	
	public String getName() {
		return name;
	}
	
	public String getValue() {
		return value;
	}
	
	public String getRaw() {
		return raw;
	}
	
	public TEFSpan getSpan() {
		return span;
	}
}
