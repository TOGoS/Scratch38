package net.nuke24.togetl.etl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import net.nuke24.togetl.hash.BitprintDigest;
import net.nuke24.togetl.hash.BitprintScheme;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.util.Sink;

/**
 * What is known about the bytes of a source document once they have all been
 * read: their SHA-256, length, and whether they start with a UTF-8 BOM.
 */
public final class SourceProvenance {
	private final String sha256;
	private final long byteLength;
	private final boolean bom;
	
	private SourceProvenance(String sha256, long byteLength, boolean bom) {
		this.sha256 = sha256;
		this.byteLength = byteLength;
		this.bom = bom;
	}
	
	public static SourceProvenance of(byte[] bytes) {
		Digester digester = new Digester();
		digester.accept(Optional.of(bytes));
		digester.accept(Optional.<byte[]>empty());
		return digester.result();
	}
	
	public String getSha256() {
		return sha256;
	}
	
	public long getByteLength() {
		return byteLength;
	}
	
	public boolean hasBom() {
		return bom;
	}
	
	/** Computes provenance from blocks as they stream past. */
	public static final class Digester implements Sink<byte[]> {
		private final MessageDigest digest;
		private final BitprintDigest bitprintDigest = new BitprintDigest();
		private final byte[] head = new byte[3];
		private int headLength;
		private long byteLength;
		private SourceProvenance result;
		private String bitprint;
		
		public Digester() {
			try {
				digest = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 is unavailable", e);
			}
		}
		
		@Override
		public void accept(Optional<byte[]> item) {
			if (!item.isPresent()) {
				boolean bom = headLength == 3
						&& head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF;
				result = new SourceProvenance(Identifiers.hex(digest.digest()), byteLength, bom);
				bitprint = BitprintScheme.hashToUrn(bitprintDigest.digest());
				return;
			}
			byte[] block = item.get();
			digest.update(block);
			bitprintDigest.update(block);
			for (int i = 0; i < block.length && headLength < head.length; i++) {
				head[headLength++] = block[i];
			}
			byteLength += block.length;
		}
		
		/** Available once the end of input has been accepted. */
		public SourceProvenance result() {
			if (result == null) {
				throw new IllegalStateException("input has not ended");
			}
			return result;
		}
		
		/** Available once the end of input has been accepted. */
		public String bitprint() {
			if (bitprint == null) {
				throw new IllegalStateException("input has not ended");
			}
			return bitprint;
		}
	}
}
