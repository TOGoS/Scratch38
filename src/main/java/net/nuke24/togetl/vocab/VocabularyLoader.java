package net.nuke24.togetl.vocab;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.nuke24.togetl.tef.TEFDocument;
import net.nuke24.togetl.tef.TEFEntry;
import net.nuke24.togetl.tef.TEFHeader;
import net.nuke24.togetl.tef.TEFReadException;
import net.nuke24.togetl.tef.TEFReader;

/**
 * Reads vocabulary definitions from TEF documents written in the namespaces
 * dialect.
 */
public final class VocabularyLoader {
	private VocabularyLoader() {
	}
	
	public static Vocabulary load(Path path) throws IOException, TEFReadException {
		Vocabulary vocabulary = new Vocabulary();
		loadInto(vocabulary, path);
		return vocabulary;
	}
	
	public static Vocabulary loadDefaults() throws IOException, TEFReadException {
		Vocabulary vocabulary = new Vocabulary();
		for (Path path : defaultVocabFiles()) {
			loadInto(vocabulary, path);
		}
		return vocabulary;
	}
	
	public static void loadInto(Vocabulary vocabulary, Path path) throws IOException, TEFReadException {
		byte[] bytes = Files.readAllBytes(path);
		String sourceUrl = path.toAbsolutePath().normalize().toUri().toString();
		TEFDocument document = TEFReader.read(sourceUrl, bytes);
		String sourceName = path.getFileName().toString();
		for (TEFEntry entry : document.getEntries()) {
			String kind = kindOf(entry.getType());
			if (kind == null) {
				continue;
			}
			String iri = entry.getValue() == null ? "" : entry.getValue().trim();
			if (iri.isEmpty()) {
				continue;
			}
			String label = firstValue(entry, "title");
			String comment = firstValue(entry, "short-description");
			if (comment == null) {
				comment = firstValue(entry, "short-descripiton");
			}
			String status = firstValue(entry, "status");
			vocabulary.define(iri, kind, label, comment, status, sourceName);
		}
	}
	
	public static List<Path> defaultVocabFiles() throws IOException {
		Path directory = resolveVocabDirectory();
		if (directory == null || !Files.isDirectory(directory)) {
			return Collections.emptyList();
		}
		List<Path> paths = new ArrayList<Path>();
		DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.tef");
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
	
	private static Path resolveVocabDirectory() {
		String root = System.getenv("TOGETL_ROOT");
		if (root != null && !root.isEmpty()) {
			Path fromRoot = Paths.get(root, "vocab");
			if (Files.isDirectory(fromRoot)) {
				return fromRoot;
			}
		}
		Path fromCwd = Paths.get("vocab");
		if (Files.isDirectory(fromCwd)) {
			return fromCwd;
		}
		return null;
	}
	
	private static String kindOf(String entryType) {
		if ("namespace".equals(entryType)) return "namespace";
		if ("type".equals(entryType)) return "class";
		if ("attribute".equals(entryType)) return "property";
		if ("datatype".equals(entryType)) return "datatype";
		if ("format".equals(entryType)) return "format";
		if ("projbase".equals(entryType)) return "project-base";
		return null;
	}
	
	private static String firstValue(TEFEntry entry, String key) {
		for (TEFHeader header : entry.getHeaders()) {
			if (header.getKey().equals(key)) {
				return header.getValue().trim();
			}
		}
		return null;
	}
}
