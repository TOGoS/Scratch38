package net.nuke24.scratch38.s0029;

/**
 * A chunk of a message tagged with role information,
 * for purposes of validating and generating HELO
 * messages with authentication headers.
 * 
 * A raw message can be converted to a list of HELOAuthTokens
 * and back losslessly -- the message is simply the concatenation
 * of all tokens' content.
 */
class Tagged<T,C> {
	public final T tag;
	public final C content;
	public Tagged(T tag, C content) {
		this.tag = tag;
		this.content = content;
	}
}
