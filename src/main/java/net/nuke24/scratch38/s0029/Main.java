package net.nuke24.scratch38.s0029;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

public class Main {
	static int failures = 0;
	
	static byte[] ascii(String s) {
		try {
			return s.getBytes("US-ASCII");
		} catch( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
	}
	
	static byte[] repeat(int value, int count) {
		byte[] b = new byte[count];
		for( int i = 0; i < count; ++i ) b[i] = (byte)value;
		return b;
	}
	
	/** Wraps a single array as a one-chunk message, for the common non-fragmented case. */
	static List<ByteChunk> chunk(byte[] b) {
		return Collections.singletonList(ByteChunk.of(b));
	}
		
	public static byte[] sha1(List<ByteChunk> message) {
		MessageDigest md = new SHA1MessageDigest();
		for( ByteChunk c : message ) md.update(c.buffer, c.offset, c.length);
		return md.digest();
	}
	
	public static byte[] hmacSha1(byte[] key, List<ByteChunk> message) {
		return HMACUtil.hmac(new SHA1MessageDigest(), SHA1MessageDigest.BLOCK_SIZE, key, message);
	}
	
	
	static final Charset UTF8 = Charset.forName("UTF-8");
	
	protected static PrintStream printStream(OutputStream os) {
		if( os instanceof PrintStream ) return (PrintStream)os;
		return new PrintStream(os, true, UTF8);
	}
	
	interface ProtoProcess<T> {
		public ProcessLike<T> start(InputStream in, OutputStream out, OutputStream err);
	}
	interface ProcessLike<T> {
		public T run();
	}
	
	static class PrintAndExitAction implements ProtoProcess<Integer>
	{
		final String outText;
		final String errText;
		final int exitCode;
		
		public PrintAndExitAction(String outText, String errText, int exitCode) {
			this.outText = outText;
			this.errText = errText;
			this.exitCode = exitCode;
		}
		
		public @Override ProcessLike<Integer> start(InputStream in, OutputStream out, OutputStream err) {
			PrintStream psOut = printStream(out);
			PrintStream psErr = printStream(err);
			return () -> {
				if( !outText.isEmpty() ) psOut.append(outText);
				if( !errText.isEmpty() ) psErr.append(errText);
				return exitCode;
			};
		}
	}
	
	static class SelfTestProcess implements ProcessLike<Integer>
	{
		final PrintStream out;
		final PrintStream err;
		
		public SelfTestProcess(PrintStream out, PrintStream err) {
			this.out = out;
			this.err = err;
		}
		
		void check(String label, String expected, String actual) {
			boolean ok = expected.equals(actual);
			if( !ok ) ++failures;
			out.println((ok ? "PASS " : "FAIL ") + label);
			if( !ok ) {
				out.println("  expected " + expected);
				out.println("  actual   " + actual);
			}
		}
		
		public @Override Integer run() {
			// SHA-1 test vectors (FIPS 180 / common)
			check("sha1(\"abc\")", "a9993e364706816aba3e25717850c26c9cd0d89d",
				StringUtil.toHex(sha1(chunk(ascii("abc")))));
			check("sha1(\"\")", "da39a3ee5e6b4b0d3255bfef95601890afd80709",
				StringUtil.toHex(sha1(chunk(ascii("")))));
			
			// HMAC-SHA1 test vectors (RFC 2202)
			check("hmac case 1", "b617318655057264e28bc0b6fb378c8ef146be00",
				StringUtil.toHex(hmacSha1(repeat(0x0b, 20), chunk(ascii("Hi There")))));
			check("hmac case 2", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
				StringUtil.toHex(hmacSha1(ascii("Jefe"), chunk(ascii("what do ya want for nothing?")))));
			check("hmac case 3", "125d7342b9ac11cd91a39af48aa17b4f63f175d3",
				StringUtil.toHex(hmacSha1(repeat(0xaa, 20), chunk(repeat(0xdd, 50)))));
			
			// The HELO spec's worked example, computed over the exact <pre> bytes.
			String heloMessage =
				"#HELO/PUT /switch1/state\n" +
				"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
				"\n" +
				"on\n";
			out.println("HELO example HMAC-SHA1 (secret \"foo\"): " +
				StringUtil.toHex(hmacSha1(ascii("foo"), chunk(ascii(heloMessage)))));
			out.println("spec claims:                          7a83499ddfaad3c92862bdecbf017f0f68d2cf3a");
			
			if( failures > 0 ) {
				out.println(failures + " test(s) FAILED");
				return 1;
			}
			out.println("All test vectors passed.");
			return 0;
		}
	}
	
	static ProtoProcess<Integer> parseMain(String[] args) {
		if( args.length == 0 ) {
			return new PrintAndExitAction("", "No command specified\n", 1);
		}
		if( "self-test".equals(args[0]) ) {
			return (in,out,err) -> new SelfTestProcess(printStream(out),printStream(err));
		}
		return new PrintAndExitAction("", "Hmm\n", 1);
	}
	
	static Integer doMain(ProtoProcess<Integer> command) {
		return Integer.valueOf(0);
	}
	
	public static void main(String[] args) {
		System.exit(parseMain(args).start(System.in, System.out, System.err).run());
	}
}
