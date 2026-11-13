package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Org lines as they are pushed in, one at a time.
 *
 * <p>Feed each line with {@link #accept(String)}, then call {@link #finish()}
 * to get the document. Lines must not contain their terminators; see
 * {@link OrgLineSplitter}.</p>
 */
public final class OrgParser implements Consumer<String> {
	private static final Pattern HEADING =
			Pattern.compile("^(\\*+)[ \t]+(.*)$", Pattern.UNIX_LINES);
	private static final Pattern TAGS =
			Pattern.compile("(:[^\\s:]+(?::[^\\s:]+)*:)\\s*$", Pattern.UNIX_LINES);
	private static final Pattern PRIORITY =
			Pattern.compile("\\[#([A-Za-z0-9])\\]", Pattern.UNIX_LINES);
	private static final Pattern PROPERTY =
			Pattern.compile("^\\s*:([^:\\s]+):[ \t]*(.*)$", Pattern.UNIX_LINES);
	private static final Pattern DRAWER =
			Pattern.compile("^\\s*:([A-Za-z0-9_@#%+-]+):[ \t]*$", Pattern.UNIX_LINES);
	private static final Pattern DRAWER_END =
			Pattern.compile("^\\s*:END:[ \t]*$", Pattern.UNIX_LINES);
	private static final Pattern PLANNING_START =
			Pattern.compile("^\\s*(SCHEDULED|DEADLINE|CLOSED):[ \t]*(.*)$", Pattern.UNIX_LINES);
	private static final Pattern PLANNING =
			Pattern.compile("(SCHEDULED|DEADLINE|CLOSED):[ \t]*(<\\d{4}-[^>\\n]*>|\\[\\d{4}-[^\\]\\n]*\\])", Pattern.UNIX_LINES);
	private static final Pattern DIRECTIVE =
			Pattern.compile("^\\s*#\\+([A-Za-z0-9_-]+):[ \t]*(.*)$", Pattern.UNIX_LINES);
	private static final Pattern BLOCK_BEGIN =
			Pattern.compile("^\\s*#\\+begin_([A-Za-z0-9_-]+)(?:[ \t]+(.*))?$", Pattern.CASE_INSENSITIVE | Pattern.UNIX_LINES);
	private static final Pattern BLOCK_END =
			Pattern.compile("^\\s*#\\+end_([A-Za-z0-9_-]+)[ \t]*$", Pattern.CASE_INSENSITIVE | Pattern.UNIX_LINES);
	private static final Pattern TABLE_ROW =
			Pattern.compile("^\\s*\\|(.*?)\\|?\\s*$", Pattern.UNIX_LINES);
	private static final Pattern TABLE_SEPARATOR =
			Pattern.compile("^\\s*\\|-.*$", Pattern.UNIX_LINES);
	private static final Pattern LINK =
			Pattern.compile("\\[\\[([^\\]]+)\\](?:\\[([^\\]]*)\\])?\\]", Pattern.UNIX_LINES);
	private static final Pattern TIMESTAMP =
			Pattern.compile("(<\\d{4}-[^>\\n]*>|\\[\\d{4}-[^\\]\\n]*\\])", Pattern.UNIX_LINES);
	private static final Pattern CHECKBOX =
			Pattern.compile("(^|\\s)\\[( |x|X|-)\\](?=\\s|$)", Pattern.UNIX_LINES);
	
	private static final List<String> DEFAULT_TODO_KEYWORDS = Arrays.asList(
			"TODO", "DONE", "WAIT", "WAITING", "HOLD", "STARTED", "NEXT",
			"CANCELLED", "CANCELED", "IDEA", "PROJ");
	
	private boolean bom;
	private int lineNumber;
	private final List<OrgHeading> headings = new ArrayList<OrgHeading>();
	private final List<OrgDirective> directives = new ArrayList<OrgDirective>();
	private final List<OrgTable> tables = new ArrayList<OrgTable>();
	private final List<OrgDiagnostic> diagnostics = new ArrayList<OrgDiagnostic>();
	private final Set<String> todoKeywords = new HashSet<String>(DEFAULT_TODO_KEYWORDS);
	private OrgHeading currentHeading;
	private int headingIndex = -1;
	private OrgTable currentTable;
	private String pendingTableName;
	private String pendingTableCaption;
	private boolean inBlock;
	private String blockName;
	private int blockLine;
	private boolean inDrawer;
	private String drawerName;
	private int drawerLine;
	
	/**
	 * Handles one line. A leading UTF-8 byte order mark (decoded as U+FEFF)
	 * on the first line is recorded and removed.
	 */
	@Override
	public void accept(String text) {
		lineNumber++;
		String line = text;
		if (lineNumber == 1 && line.startsWith("\uFEFF")) {
			bom = true;
			line = line.substring(1);
		}
		
		if (inBlock) {
			Matcher blockEnd = BLOCK_END.matcher(line);
			if (blockEnd.matches() && blockEnd.group(1).equalsIgnoreCase(blockName)) {
				inBlock = false;
				blockName = null;
			}
			return;
		}
		Matcher blockBegin = BLOCK_BEGIN.matcher(line);
		if (blockBegin.matches()) {
			inBlock = true;
			blockName = blockBegin.group(1);
			blockLine = lineNumber;
			currentTable = null;
			pendingTableName = null;
			pendingTableCaption = null;
			return;
		}
		
		Matcher headingMatch = HEADING.matcher(line);
		if (headingMatch.matches()) {
			if (inDrawer) {
				diagnostics.add(unclosedDrawer(drawerName, drawerLine));
			}
			currentHeading = parseHeading(headingMatch, todoKeywords, lineNumber);
			headings.add(currentHeading);
			headingIndex++;
			inDrawer = false;
			drawerName = null;
			currentTable = null;
			pendingTableName = null;
			pendingTableCaption = null;
			return;
		}
		
		if (inDrawer) {
			if (DRAWER_END.matcher(line).matches()) {
				inDrawer = false;
				drawerName = null;
				return;
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
			return;
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
			return;
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
			return;
		}
		currentTable = null;
		
		if (line.trim().isEmpty()) {
			return;
		}
		pendingTableName = null;
		pendingTableCaption = null;
		
		if (currentHeading == null) {
			return;
		}
		
		if (isComment(line)) {
			return;
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
			return;
		}
		
		Matcher drawerMatch = DRAWER.matcher(line);
		if (drawerMatch.matches()) {
			inDrawer = true;
			drawerName = drawerMatch.group(1);
			drawerLine = lineNumber;
			return;
		}
		
		parseMarkers(currentHeading, line, lineNumber);
	}
	
	/** Ends the input and returns the parsed document. Call once, after the last line. */
	public OrgDocument finish() {
		if (inBlock) {
			diagnostics.add(new OrgDiagnostic(blockLine,
					"unclosed block #+begin_" + blockName + "; following lines are ignored"));
		}
		if (inDrawer) {
			diagnostics.add(unclosedDrawer(drawerName, drawerLine));
		}
		return new OrgDocument(bom, headings, directives, tables, diagnostics);
	}
	
	private static OrgDiagnostic unclosedDrawer(String drawerName, int line) {
		return new OrgDiagnostic(line, "unclosed drawer :" + drawerName + ": has no :END:");
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
}
