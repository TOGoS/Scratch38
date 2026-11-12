package net.nuke24.togetl.dialect;

import java.util.Arrays;
import java.util.List;

import net.nuke24.togetl.mapping.MappingTable;
import net.nuke24.togetl.mapping.MappingTEFDialect;

/**
 * Available TEF dialects. Explicit selection is an MVP requirement; automatic
 * detection is a post-MVP idea.
 */
public final class Dialects {
	public static final String PARTS = "parts";
	public static final String NAMESPACES = "namespaces";
	
	private static final List<String> NAMES =
			Arrays.asList(GenericTEFDialect.NAME, PARTS, NAMESPACES);
			
	private Dialects() {
	}
	
	public static TEFDialect byName(String name) {
		if (GenericTEFDialect.NAME.equals(name)) {
			return new GenericTEFDialect();
		}
		if (PARTS.equals(name)) {
			return new MappingTEFDialect(PARTS, MappingTable.load(PARTS));
		}
		if (NAMESPACES.equals(name)) {
			return new MappingTEFDialect(NAMESPACES, MappingTable.load(NAMESPACES));
		}
		throw new IllegalArgumentException(
				"Unknown dialect: " + name + " (expected one of " + NAMES + ")");
	}
	
	public static List<String> names() {
		return NAMES;
	}
}
