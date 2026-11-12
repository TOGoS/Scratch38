package net.nuke24.togetl.org;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Org structure reader for headings and common markers.
 */
public final class OrgReader {
	private static final Pattern HEADING =
			Pattern.compile("^(\\*+)\\s+(.*)$");
	private static final Pattern PROPERTY =
			Pattern.compile("^:([^:]+):\\s*(.*)$");
	private static final Pattern LINK =
			Pattern.compile("\\[\\[([^\\]]+)\\](?:\\[([^\\]]*)\\])?\\]");
	private static final Pattern TIMESTAMP =
			Pattern.compile("(<\\d{4}-[^>\\n]*>|\\[\\d{4}-[^\\]\\n]*\\])");
	private static final Pattern CHECKBOX =
			Pattern.compile("(^|\\s)\\[( |x|X|-)\\](?=\\s|$)");
			
	private OrgReader() {
	}
	
	public static OrgDocument read(String sourceIri, byte[] bytes) {
		boolean bom = hasUtf8Bom(bytes);
		String text;
		if (bom) {
			text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		} else {
			text = new String(bytes, StandardCharsets.UTF_8);
		}
		
		String[] lines = text.split("\n", -1);
		List<OrgHeading> headings = new ArrayList<OrgHeading>();
		OrgHeading currentHeading = null;
		boolean inProperties = false;
		for (int i = 0; i < lines.length; i++) {
			int lineNumber = i + 1;
			String line = stripTrailingCr(lines[i]);
			
			Matcher headingMatch = HEADING.matcher(line);
			if (headingMatch.matches()) {
				int level = headingMatch.group(1).length();
				String title = headingMatch.group(2).trim();
				currentHeading = new OrgHeading(level, title, lineNumber);
				headings.add(currentHeading);
				inProperties = false;
				continue;
			}
			
			if (currentHeading == null) {
				continue;
			}
			
			if (":PROPERTIES:".equals(line)) {
				inProperties = true;
				continue;
			}
			if (inProperties) {
				if (":END:".equals(line)) {
					inProperties = false;
					continue;
				}
				Matcher propertyMatch = PROPERTY.matcher(line);
				if (propertyMatch.matches()) {
					String key = propertyMatch.group(1).trim();
					String value = propertyMatch.group(2).trim();
					if ("CUSTOM_ID".equalsIgnoreCase(key) && !value.isEmpty()) {
						currentHeading.setCustomId(value);
					}
				}
				continue;
			}
			
			parseMarkers(currentHeading, line, lineNumber);
		}
		
		return new OrgDocument(sourceIri, bytes.length, bom, headings);
	}
	
	private static void parseMarkers(OrgHeading heading, String line, int lineNumber) {
		Matcher linkMatcher = LINK.matcher(line);
		while (linkMatcher.find()) {
			heading.addLink(new OrgLink(
					linkMatcher.group(1),
					linkMatcher.group(2),
					lineNumber,
					linkMatcher.start() + 1));
		}
		
		Matcher timestampMatcher = TIMESTAMP.matcher(line);
		while (timestampMatcher.find()) {
			heading.addTimestamp(new OrgTimestamp(
					timestampMatcher.group(1),
					lineNumber,
					timestampMatcher.start() + 1));
		}
		
		Matcher checkboxMatcher = CHECKBOX.matcher(line);
		while (checkboxMatcher.find()) {
			heading.addCheckbox(new OrgCheckbox(
					checkboxState(checkboxMatcher.group(2)),
					lineNumber,
					checkboxMatcher.start(2) + 1));
		}
	}
	
	private static String checkboxState(String marker) {
		if (" ".equals(marker)) {
			return "unchecked";
		}
		if ("-".equals(marker)) {
			return "partial";
		}
		return "checked";
	}
	
	private static String stripTrailingCr(String line) {
		if (line.endsWith("\r")) {
			return line.substring(0, line.length() - 1);
		}
		return line;
	}
	
	private static boolean hasUtf8Bom(byte[] bytes) {
		return bytes.length >= 3
				&& (bytes[0] & 0xFF) == 0xEF
				&& (bytes[1] & 0xFF) == 0xBB
				&& (bytes[2] & 0xFF) == 0xBF;
	}
}
