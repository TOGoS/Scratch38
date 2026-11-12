package net.nuke24.togetl.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.nuke24.togetl.togetltsv.AliasTable;

/**
 * A TSV mapping table: entry-type rules followed by header rules.
 *
 * <pre>
 * #entry
 * entryType  subjectTemplate  type  note
 * #field
 * field  entryType  header  predicate  valueParser  cardinality  note
 * </pre>
 *
 * <p>A field rule with entry type {@code *} applies to every entry type that
 * does not have a more specific rule.</p>
 */
public final class MappingTable {
	private final String name;
	private final Map<String, EntryRule> entryRules = new LinkedHashMap<String, EntryRule>();
	private final Map<String, FieldRule> fieldRules = new LinkedHashMap<String, FieldRule>();
	private final Map<String, FieldRule> wildcardFieldRules = new LinkedHashMap<String, FieldRule>();
	
	private MappingTable(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public EntryRule entryRule(String entryType) {
		return entryRules.get(entryType);
	}
	
	public FieldRule fieldRule(String entryType, String header) {
		FieldRule rule = fieldRules.get(entryType + '\u0000' + header);
		if (rule != null) {
			return rule;
		}
		return wildcardFieldRules.get(header);
	}
	
	public Set<String> getUsedIris() {
		Set<String> iris = new TreeSet<String>();
		for (FieldRule rule : fieldRules.values()) {
			iris.add(rule.getPredicate());
		}
		for (FieldRule rule : wildcardFieldRules.values()) {
			iris.add(rule.getPredicate());
		}
		for (EntryRule rule : entryRules.values()) {
			if (rule.getTypeIri() != null) {
				iris.add(rule.getTypeIri());
			}
		}
		return Collections.unmodifiableSet(iris);
	}
	
	public List<EntryRule> getEntryRules() {
		return Collections.unmodifiableList(new ArrayList<EntryRule>(entryRules.values()));
	}
	
	public List<FieldRule> getFieldRules() {
		List<FieldRule> rules = new ArrayList<FieldRule>(fieldRules.values());
		rules.addAll(wildcardFieldRules.values());
		return Collections.unmodifiableList(rules);
	}
	
	public static MappingTable load(String nameOrPath) {
		Path path = resolve(nameOrPath);
		try {
			return parse(path.getFileName().toString(), Files.readAllLines(path, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read mapping table " + path + ": " + e.getMessage(), e);
		}
	}
	
	public static List<Path> defaultFiles() throws IOException {
		Path directory = resolveDirectory();
		if (directory == null || !Files.isDirectory(directory)) {
			return Collections.emptyList();
		}
		List<Path> paths = new ArrayList<Path>();
		java.nio.file.DirectoryStream<Path> stream =
				Files.newDirectoryStream(directory, "*.tsv");
		try {
			for (Path path : stream) {
				paths.add(path);
			}
		} finally {
			stream.close();
		}
		Collections.sort(paths);
		return paths;
	}
	
	public static MappingTable parse(String name, List<String> lines) {
		MappingTable table = new MappingTable(name);
		AliasTable aliases = AliasTable.withDefaults();
		String section = "";
		int lineNumber = 0;
		
		for (String rawLine : lines) {
			lineNumber++;
			String line = rawLine;
			if (line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			if (line.trim().isEmpty()) {
				continue;
			}
			if (line.startsWith("#")) {
				String directive = line.trim();
				if ("#entry".equals(directive)) {
					section = "entry";
				} else if ("#field".equals(directive)) {
					section = "field";
				}
				continue;
			}
			
			String[] parts = line.split("\t", -1);
			try {
				if ("entry".equals(section)) {
					parseEntry(table, aliases, parts);
				} else if ("field".equals(section)) {
					parseField(table, aliases, parts);
				} else {
					throw new IllegalArgumentException("row outside a section");
				}
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						name + " line " + lineNumber + ": " + e.getMessage(), e);
			}
		}
		return table;
	}
	
	private static void parseEntry(MappingTable table, AliasTable aliases, String[] parts) {
		if ("entryType".equals(parts[0])) {
			return;
		}
		if (parts.length < 3) {
			throw new IllegalArgumentException("entry row needs entryType, subjectTemplate, and type");
		}
		String entryType = parts[0].trim();
		String subjectTemplate = parts[1].trim();
		String type = parts[2].trim();
		String note = parts.length > 3 ? parts[3].trim() : "";
		if (entryType.isEmpty() || subjectTemplate.isEmpty()) {
			throw new IllegalArgumentException("entry row has an empty entryType or subjectTemplate");
		}
		table.entryRules.put(
				entryType,
				new EntryRule(
						entryType,
						subjectTemplate,
						type.isEmpty() ? null : aliases.expand(type),
						parseAsWritten(note)));
	}
	
	private static void parseField(MappingTable table, AliasTable aliases, String[] parts) {
		if (parts.length >= 2 && "field".equals(parts[0]) && "entryType".equals(parts[1])) {
			return;
		}
		if (parts.length < 6) {
			throw new IllegalArgumentException(
					"field row needs field, entryType, header, predicate, valueParser, cardinality");
		}
		String entryType = parts[1].trim();
		String header = parts[2].trim();
		String predicate = parts[3].trim();
		String valueParser = parts[4].trim();
		String cardinality = parts[5].trim();
		String note = parts.length > 6 ? parts[6].trim() : "";
		if (entryType.isEmpty() || header.isEmpty() || predicate.isEmpty() || valueParser.isEmpty()) {
			throw new IllegalArgumentException("field row has an empty required column");
		}
		if (!"1".equals(cardinality) && !"?".equals(cardinality) && !"*".equals(cardinality)) {
			throw new IllegalArgumentException("invalid cardinality: " + cardinality);
		}
		FieldRule rule = new FieldRule(
				entryType,
				header,
				aliases.expand(predicate),
				valueParser,
				cardinality,
				parseAsWritten(note));
		if ("*".equals(entryType)) {
			table.wildcardFieldRules.put(header, rule);
		} else {
			table.fieldRules.put(entryType + '\u0000' + header, rule);
		}
	}
	
	private static String parseAsWritten(String note) {
		if (note.startsWith("asWritten=")) {
			return note.substring("asWritten=".length());
		}
		return null;
	}
	
	private static Path resolve(String nameOrPath) {
		Path direct = Paths.get(nameOrPath);
		if (Files.isRegularFile(direct)) {
			return direct;
		}
		String fileName = nameOrPath.endsWith(".tsv") ? nameOrPath : nameOrPath + ".tsv";
		Path directory = resolveDirectory();
		if (directory != null) {
			Path fromDirectory = directory.resolve(fileName);
			if (Files.isRegularFile(fromDirectory)) {
				return fromDirectory;
			}
		}
		throw new IllegalArgumentException("Mapping table not found: " + nameOrPath);
	}
	
	private static Path resolveDirectory() {
		String root = System.getenv("TOGETL_ROOT");
		if (root != null && !root.isEmpty()) {
			Path fromRoot = Paths.get(root, "mappings");
			if (Files.isDirectory(fromRoot)) {
				return fromRoot;
			}
		}
		Path fromCwd = Paths.get("mappings");
		if (Files.isDirectory(fromCwd)) {
			return fromCwd;
		}
		return null;
	}
}
