package net.nuke24.scratch38.s0029;

public enum HELOChunkType {
	DELIMITER, // Slashes, whitespace, newlines etc
	HELO_MAGIC, // "#HELO"
	HELO_VERSION,
	METHOD,
	RESOURCE_PATH,
	HEADER_NAME,
	HEADER_VALUE,
	JUNK, // Anything that seems out-of-place
	PAYLOAD
}
