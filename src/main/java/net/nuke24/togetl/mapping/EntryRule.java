package net.nuke24.togetl.mapping;

import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;

/**
 * One entry-type mapping rule from a mapping table.
 */
public final class EntryRule {
	private final String entryType;
	private final String subjectTemplate;
	private final String typeIri;
	private final String asWritten;
	
	EntryRule(String entryType, String subjectTemplate, String typeIri, String asWritten) {
		this.entryType = entryType;
		this.subjectTemplate = subjectTemplate;
		this.typeIri = typeIri;
		this.asWritten = asWritten;
	}
	
	public String getEntryType() {
		return entryType;
	}
	
	public String getSubjectTemplate() {
		return subjectTemplate;
	}
	
	public String getTypeIri() {
		return typeIri;
	}
	
	public String getAsWritten() {
		return asWritten;
	}
	
	/**
	 * Expands {@code ${value}}, {@code ${valueEncoded}}, {@code ${ordinal}},
	 * {@code ${document}}, {@code ${documentHash}}, and
	 * {@code ${header:name}}.
	 *
	 * @return the subject IRI text, or null when the rule cannot produce one.
	 */
	public String buildSubject(
			TEFEntry entry,
			String documentIri,
			String documentHash,
			int ordinal
	) {
		String result = subjectTemplate;
		String value = entry.getValue() == null ? "" : entry.getValue().trim();
		if (result.contains("${valueEncoded}")) {
			if (value.isEmpty()) {
				return null;
			}
			result = result.replace("${valueEncoded}", net.nuke24.togetl.ident.Identifiers.encodeKey(value));
		}
		if (result.contains("${value}")) {
			if (value.isEmpty()) {
				return null;
			}
			result = result.replace("${value}", value);
		}
		result = result.replace("${document}", documentIri);
		result = result.replace("${documentHash}", documentHash);
		result = result.replace("${ordinal}", Integer.toString(ordinal));
		int from = 0;
		while (true) {
			int start = result.indexOf("${header:", from);
			if (start < 0) {
				break;
			}
			int end = result.indexOf('}', start);
			if (end < 0) {
				return null;
			}
			String name = result.substring(start + "${header:".length(), end);
			String headerValue = firstHeaderValue(entry, name);
			if (headerValue == null) {
				return null;
			}
			result = result.substring(0, start) + headerValue + result.substring(end + 1);
			from = start + headerValue.length();
		}
		result = result.trim();
		if (result.isEmpty() || containsWhitespace(result)) {
			return null;
		}
		return result;
	}
	
	private static String firstHeaderValue(TEFEntry entry, String name) {
		for (TEFHeader header : entry.getHeaders()) {
			if (header.getKey().equals(name)) {
				return header.getValue().trim();
			}
		}
		return null;
	}
	
	private static boolean containsWhitespace(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}
}
