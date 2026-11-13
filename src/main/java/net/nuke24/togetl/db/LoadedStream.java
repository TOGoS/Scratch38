package net.nuke24.togetl.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.nuke24.togetl.rdf.Statement;

/**
 * One TOGETLTSV stream read by the loader, together with its content hash.
 */
public final class LoadedStream {
	private final String name;
	private final String sha256;
	private final List<Statement> statements;
	
	public LoadedStream(String name, String sha256, List<Statement> statements) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Stream name must not be empty");
		}
		if (sha256 == null || sha256.isEmpty()) {
			throw new IllegalArgumentException("Stream hash must not be empty");
		}
		if (statements == null) {
			throw new IllegalArgumentException("Statements must not be null");
		}
		this.name = name;
		this.sha256 = sha256;
		this.statements = Collections.unmodifiableList(new ArrayList<Statement>(statements));
	}
	
	public String getName() {
		return name;
	}
	
	public String getSha256() {
		return sha256;
	}
	
	public List<Statement> getStatements() {
		return statements;
	}
}
