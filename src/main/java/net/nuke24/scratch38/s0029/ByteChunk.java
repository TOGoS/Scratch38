package net.nuke24.scratch38.s0029;

import java.util.Collections;
import java.util.List;

public class ByteChunk implements ByteBlob {
	static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
	public static final ByteChunk EMPTY = new ByteChunk(EMPTY_BYTE_ARRAY, 0, 0);
	
	public final byte[] buffer;
	public final int offset;
	public final int length;
	
	protected final List<ByteChunk> listOfSelf;
	
	public ByteChunk(byte[] buffer, int offset, int length) {
		this.buffer = buffer;
		this.offset = offset;
		this.length = length;
		this.listOfSelf = length == 0 ? Collections.emptyList() : Collections.singletonList(this);
	}
	
	/** View of the entire given array. */
	public static ByteChunk of(byte[] buffer) {
		return
			buffer.length == 0 ? EMPTY :
			new ByteChunk(buffer, 0, buffer.length);
	}
	
	/** Content-based, consistent with any other {@link ByteBlob} implementation. */
	@Override
	public boolean equals(Object obj) {
		if( this == obj ) return true;
		if( !(obj instanceof ByteBlob) ) return false;
		return ByteBlobs.contentEquals(this, (ByteBlob)obj);
	}
	
	public @Override int length() { return length; }
	
	public @Override List<ByteChunk> getChunks() { return this.listOfSelf; }
	
	@Override
	public int hashCode() {
		return ByteBlobs.contentHashCode(this);
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
		sb.append("}");
		return sb.toString();
	}
}
