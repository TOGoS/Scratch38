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
	
	@Override
	public boolean equals(Object obj) {
		if( this == obj ) return true;
		if( !(obj instanceof Tagged) ) return false;
		Tagged<?,?> other = (Tagged<?,?>)obj;
		return
			(tag == null ? other.tag == null : tag.equals(other.tag)) &&
			(content == null ? other.content == null : content.equals(other.content));
	}
	
	@Override
	public int hashCode() {
		int result = tag == null ? 0 : tag.hashCode();
		result = 31 * result + (content == null ? 0 : content.hashCode());
		return result;
	}
	
	@Override
	public String toString() {
		return "Tagged(" + tag + ", " + content + ")";
	}
}
