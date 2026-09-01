package net.nuke24.scratch38.s0029;

enum HELOAuthChunkType {
	NORMAL,
	/** Just the "auth" at the beginning of a header line */
	AUTH_HEADER,
	AUTH_SCHEME,
	/** Indicates the value in the auth header following a unrecognized auth scheme */
	AUTH_UNKNOWN_DATA,
	/** Minimum / maxumum nonce values, sent from server */
	NONCE_MIN,
	NONCE_MAX,
	/** ACtual nonce value, sent to server */
	NONCE,
	/** Hash or placeholder of hash */
	HASH
}
