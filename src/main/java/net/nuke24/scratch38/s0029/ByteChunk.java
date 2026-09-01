package net.nuke24.scratch38.s0029;

public class ByteChunk {
	public final byte[] buffer;
	public final int offset;
	public final int length;
	
	public ByteChunk(byte[] buffer, int offset, int length) {
		this.buffer = buffer;
		this.offset = offset;
		this.length = length;
	}
	
	/** View of the entire given array. */
	public static ByteChunk of(byte[] buffer) {
		return new ByteChunk(buffer, 0, buffer.length);
	}
	
	@Override
	public boolean equals(Object obj) {
		if( this == obj ) return true;
		if( !(obj instanceof ByteChunk) ) return false;
		ByteChunk other = (ByteChunk)obj;
		if( this.length != other.length ) return false;
		for( int i=0; i<this.length; ++i ) {
			if( this.buffer[this.offset+i] != other.buffer[other.offset+i] ) return false;
		}
		return true;
	}
	
	@Override
	public int hashCode() {
		int hash = 1;
		for( int i=0; i<this.length; ++i ) {
			hash = 31 * hash + this.buffer[this.offset+i];
		}
		return hash;
	}
	
	private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ByteChunk{");
		sb.append("data:,");
		for( int i=0; i<this.length; ++i ) {
			int b = this.buffer[this.offset+i] & 0xFF;
			char c = (char)b;
			if( (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
				c == '-' || c == '_' || c == '.' || c == '~' )
			{
				sb.append(c);
			} else {
				sb.append('%');
				sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
				sb.append(HEX_DIGITS[b & 0xF]);
			}
		}
		sb.append("%7D");
		return sb.toString();
	}
}
