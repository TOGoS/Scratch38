package net.nuke24.togetl.org;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Org structure reader for headings and common markers.
 */
public final class OrgReader {
	private static final Pattern HEADING =
			Pattern.compile("^(\\*+)[ \t]+(.*)$");
	private static final Pattern TAGS =
			Pattern.compile("(:[^\\s:]+(?::[^\\s:]+)*:)\\s*$");
	private static final Pattern PRIORITY =
			Pattern.compile("\\[#([A-Za-z0-9])\\]");
	private static final Pattern PROPERTY =
			Pattern.compile("^\\s*:([^:\\s]+):[ \t]*(.*)$");
	private static final Pattern DRAWER =
			Pattern.compile("^\\s*:([A-Za-z0-9_@#%+-]+):[ \t]*$");
	private static final Pattern DRAWER_END =
			Pattern.compile("^\\s*:END:[ \t]*$");
	private static final Pattern PLANNING_START =
			Pattern.compile("^\\s*(SCHEDULED|DEADLINE|CLOSED):[ \t]*(.*)$");
	private static final Pattern PLANNING =
			Pattern.compile("(SCHEDULED|DEADLINE|CLOSED):[ \t]*(<\\d{4}-[^>\\n]*>|\\[\\d{4}-[^\\]\\n]*\\])");
	private static final Pattern DIRECTIVE =
			Pattern.compile("^\\s*#\\+([A-Za-z0-9_-]+):[ \t]*(.*)$");
	private static final Pattern BLOCK_BEGIN =
			Pattern.compile("^\\s*#\\+begin_([A-Za-z0-9_-]+)(?:[ \t]+(.*))?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BLOCK_END =
			Pattern.compile("^\\s*#\\+end_([A-Za-z0-9_-]+)[ \t]*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TABLE_ROW =
			Pattern.compile("^\\s*\\|(.*)\\|\\s*$");
	private static final Pattern TABLE_SEPARATOR =
			Pattern.compile("^\\s*\\|[-+]+\\|\\s*$");
	private static final Pattern LINK =
			Pattern.compile("\\[\\[([^\\]]+)\\](?:\\[([^\\]]*)\\])?\\]");
	private static final Pattern TIMESTAMP =
			Pattern.compile("(<\\d{4}-[^>\\n]*>|\\[\\d{4}-[^\\]\\n]*\\])");
	private static final Pattern CHECKBOX =
			Pattern.compile("(^|\\s)\\[( |x|X|-)\\](?=\\s|$)");
			
	private static final List<String> DEFAULT_TODO_KEYWORDS = Arrays.asList(
			"TODO", "DONE", "WAIT", "WAITING", "HOLD", "STARTED", "NEXT",
			"CANCELLED", "CANCELED", "IDEA", "PROJ");
			
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
		List<OrgDirective> directives = new ArrayList<OrgDirective>();
		List<OrgTable> tables = new ArrayList<OrgTable>();
		Set<String> todoKeywords = new HashSet<String>(DEFAULT_TODO_KEYWORDS);
		OrgHeading currentHeading = null;
		int headingIndex = -1;
		OrgTable currentTable = null;
		String pendingTableName = null;
		String pendingTableCaption = null;
		boolean inBlock = false;
		String blockName = null;
		boolean inDrawer = false;
		String drawerName = null;
		for (int i = 0; i < lines.length; i++) {
			int lineNumber = i + 1;
			String line = stripTrailingCr(lines[i]);
			
			if (inBlock) {
				Matcher blockEnd = BLOCK_END.matcher(line);
				if (blockEnd.matches() && blockEnd.group(1).equalsIgnoreCase(blockName)) {
					inBlock = false;
					blockName = null;
				}
				continue;
			}
			Matcher blockBegin = BLOCK_BEGIN.matcher(line);
			if (blockBegin.matches()) {
				inBlock = true;
				blockName = blockBegin.group(1);
				currentTable = null;
				pendingTableName = null;
				pendingTableCaption = null;
				continue;
			}
			
			Matcher headingMatch = HEADING.matcher(line);
			if (headingMatch.matches()) {
				currentHeading = parseHeading(headingMatch, todoKeywords, lineNumber);
				headings.add(currentHeading);
				headingIndex++;
				inDrawer = false;
				drawerName = null;
				currentTable = null;
				pendingTableName = null;
				pendingTableCaption = null;
				continue;
			}
			
			if (inDrawer) {
				if (DRAWER_END.matcher(line).matches()) {
					inDrawer = false;
					drawerName = null;
					continue;
				}
				if ("PROPERTIES".equalsIgnoreCase(drawerName)) {
					Matcher propertyMatch = PROPERTY.matcher(line);
					if (propertyMatch.matches()) {
						String key = propertyMatch.group(1).trim();
						String value = propertyMatch.group(2).trim();
						currentHeading.addProperty(new OrgProperty(
								key, value, lineNumber, propertyMatch.start(1) + 1));
						if ("CUSTOM_ID".equalsIgnoreCase(key) && !value.isEmpty()) {
							currentHeading.setCustomId(value);
						}
					}
				}
				continue;
			}
			
			Matcher directiveMatch = DIRECTIVE.matcher(line);
			if (directiveMatch.matches()) {
				String name = directiveMatch.group(1);
				String value = directiveMatch.group(2).trim();
				directives.add(new OrgDirective(
						name, value, lineNumber, directiveMatch.start(1) + 1, headingIndex));
				updateTodoKeywords(name, value, todoKeywords);
				if ("NAME".equalsIgnoreCase(name)) {
					pendingTableName = value;
				} else if ("CAPTION".equalsIgnoreCase(name)) {
					pendingTableCaption = value;
				} else {
					pendingTableName = null;
					pendingTableCaption = null;
				}
				currentTable = null;
				continue;
			}
			
			Matcher tableRow = TABLE_ROW.matcher(line);
			if (tableRow.matches()) {
				if (currentTable == null) {
					currentTable = new OrgTable(
							pendingTableName, pendingTableCaption, lineNumber, headingIndex);
					tables.add(currentTable);
					pendingTableName = null;
					pendingTableCaption = null;
				}
				String rowText = tableRow.group(1);
				boolean separator = TABLE_SEPARATOR.matcher(line).matches();
				List<String> cells = new ArrayList<String>();
				for (String cell : rowText.split("\\|", -1)) {
					cells.add(cell.trim());
				}
				currentTable.addRow(new OrgTableRow(cells, separator, lineNumber));
				continue;
			}
			currentTable = null;
			
			if (line.trim().isEmpty()) {
				continue;
			}
			pendingTableName = null;
			pendingTableCaption = null;
			
			if (currentHeading == null) {
				continue;
			}
			
			if (isComment(line)) {
				continue;
			}
			
			Matcher planningStart = PLANNING_START.matcher(line);
			if (planningStart.matches()) {
				Matcher planningMatch = PLANNING.matcher(line);
				while (planningMatch.find()) {
					currentHeading.addPlanning(new OrgPlanning(
							planningMatch.group(1),
							new OrgTimestamp(
									planningMatch.group(2),
									lineNumber,
									planningMatch.start(2) + 1)));
				}
				continue;
			}
			
			Matcher drawerMatch = DRAWER.matcher(line);
			if (drawerMatch.matches()) {
				inDrawer = true;
				drawerName = drawerMatch.group(1);
				continue;
			}
			
			parseMarkers(currentHeading, line, lineNumber);
		}
		
		return new OrgDocument(sourceIri, bytes.length, bom, headings, directives, tables);
	}
	
	private static OrgHeading parseHeading(
			Matcher headingMatch,
			Set<String> todoKeywords,
			int lineNumber
	) {
		int level = headingMatch.group(1).length();
		String rest = headingMatch.group(2).trim();
		List<String> tags = new ArrayList<String>();
		
		Matcher tagMatch = TAGS.matcher(rest);
		if (tagMatch.find()
				&& (tagMatch.start() == 0
						|| Character.isWhitespace(rest.charAt(tagMatch.start() - 1)))) {
			String tagGroup = tagMatch.group(1);
			for (String tag : tagGroup.split(":")) {
				if (!tag.isEmpty()) {
					tags.add(tag);
				}
			}
			rest = rest.substring(0, tagMatch.start()).trim();
		}
		
		String todoKeyword = null;
		int firstSpace = firstWhitespace(rest);
		if (firstSpace > 0) {
			String firstToken = rest.substring(0, firstSpace);
			if (todoKeywords.contains(firstToken)) {
				todoKeyword = firstToken;
				rest = rest.substring(firstSpace).trim();
			}
		}
		
		String priority = null;
		Matcher priorityMatch = PRIORITY.matcher(rest);
		if (priorityMatch.lookingAt()
				&& (priorityMatch.end() == rest.length()
						|| Character.isWhitespace(rest.charAt(priorityMatch.end())))) {
			priority = priorityMatch.group(1);
			rest = rest.substring(priorityMatch.end()).trim();
		}
		
		OrgHeading heading = new OrgHeading(level, rest, lineNumber);
		heading.setTodoKeyword(todoKeyword);
		heading.setPriority(priority);
		for (String tag : tags) {
			heading.addTag(tag);
		}
		parseMarkers(heading, rest, lineNumber);
		return heading;
	}
	
	private static int firstWhitespace(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				return i;
			}
		}
		return -1;
	}
	
	private static void updateTodoKeywords(
			String directiveName,
			String directiveValue,
			Set<String> todoKeywords
	) {
		String name = directiveName.toUpperCase(Locale.ROOT);
		if (!"TODO".equals(name) && !"SEQ_TODO".equals(name) && !"TYP_TODO".equals(name)) {
			return;
		}
		for (String token : directiveValue.trim().split("\\s+")) {
			if (token.isEmpty() || "|".equals(token)) {
				continue;
			}
			int paren = token.indexOf('(');
			if (paren > 0) {
				token = token.substring(0, paren);
			}
			if (!token.isEmpty()) {
				todoKeywords.add(token);
			}
		}
	}
	
	private static boolean isComment(String line) {
		String trimmed = line.trim();
		return trimmed.startsWith("#") && !trimmed.startsWith("#+");
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
