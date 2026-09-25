package net.nuke24.scratch38.s0029;

import static net.nuke24.scratch38.s0029.ByteArrayUtil.ascii;
import static net.nuke24.scratch38.s0029.ByteArrayUtil.repeat;
import static net.nuke24.scratch38.s0029.StringUtil.byteChunk;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
	static int failures = 0;
			
	public static byte[] sha1(ByteBlob message) {
		MessageDigest md = new SHA1MessageDigest();
		for( ByteChunk c : message.getChunks() ) md.update(c.buffer, c.offset, c.length);
		return md.digest();
	}
	
	public static byte[] hmacSha1(byte[] key, ByteBlob message) {
		return HMACUtil.hmac(new SHA1MessageDigest(), SHA1MessageDigest.BLOCK_SIZE, key, message);
	}
	
	protected static PrintStream printStream(OutputStream os) {
		if( os instanceof PrintStream ) return (PrintStream)os;
		try {
			return new PrintStream(os, true, "UTF-8");
		} catch( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
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
				StringUtil.toHex(sha1(ByteChunk.of(ascii("abc")))));
			check("sha1(\"\")", "da39a3ee5e6b4b0d3255bfef95601890afd80709",
				StringUtil.toHex(sha1(ByteChunk.of(ascii("")))));
			
			// HMAC-SHA1 test vectors (RFC 2202)
			check("hmac case 1", "b617318655057264e28bc0b6fb378c8ef146be00",
				StringUtil.toHex(hmacSha1(repeat(0x0b, 20), ByteChunk.of(ascii("Hi There")))));
			check("hmac case 2", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79",
				StringUtil.toHex(hmacSha1(ascii("Jefe"), ByteChunk.of(ascii("what do ya want for nothing?")))));
			check("hmac case 3", "125d7342b9ac11cd91a39af48aa17b4f63f175d3",
				StringUtil.toHex(hmacSha1(repeat(0xaa, 20), ByteChunk.of(repeat(0xdd, 50)))));
			
			// The HELO spec's worked example, computed over the exact <pre> bytes.
			String heloMessage =
				"#HELO/PUT /switch1/state\n" +
				"auth NHS1 12346;XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" +
				"\n" +
				"on\n";
			out.println("HELO example HMAC-SHA1 (secret \"foo\"): " +
				StringUtil.toHex(hmacSha1(ascii("foo"), ByteChunk.of(ascii(heloMessage)))));
			out.println("spec claims:                          7a83499ddfaad3c92862bdecbf017f0f68d2cf3a");
			
			if( failures > 0 ) {
				out.println(failures + " test(s) FAILED");
				return 1;
			}
			out.println("All test vectors passed.");
			return 0;
		}
	}
	
	interface ThrowingSupplier<R, E extends Throwable> {
		public R get() throws E;
	}
	
	static <T> void write(ByteBlob blob, OutputStream os) throws IOException {
		for( ByteChunk subChunk : blob.getChunks() ) {
			os.write(subChunk.buffer, subChunk.offset, subChunk.length);
		}
	}
	
	static class SignProcess implements ProcessLike<Integer> {
		public final String secret;
		public final String nonce;
		public final ThrowingSupplier<InputStream,IOException> inSupplier;
		public final PrintStream out;
		public final PrintStream err;
		
		public SignProcess(String secret, String nonce, ThrowingSupplier<InputStream,IOException> inSupplier, PrintStream out, PrintStream err) {
			this.secret = secret;
			this.nonce = nonce;
			this.inSupplier = inSupplier;
			this.out = out;
			this.err = err;
		}
		
		public @Override Integer run() {
			ByteArrayOutputStream collector = new ByteArrayOutputStream();
			try( InputStream is = inSupplier.get() ) {
				byte[] buf = new byte[65536];
				int z;
				while( (z = is.read(buf, 0, buf.length)) > 0 ) {
					collector.write(buf, 0, z);
				}
			} catch( IOException e ) {
				e.printStackTrace(err);
				return Integer.valueOf(1);
			}
			
			List<Tagged<HELOChunkType, ByteBlob>> heloTokens = HELOTokenizer.tokenize(collector.toByteArray());
			
			ByteBlob signed = SignatureUtil.sign(byteChunk(nonce), heloTokens, content -> {
				return byteChunk(StringUtil.toHex(hmacSha1(
					ByteArrayUtil.ascii(secret),
					content
				)));	
			});
			
			try {
				write(signed, out);
			} catch( IOException e ) {
				err.print("Error writing output");
				e.printStackTrace(err);
				return Integer.valueOf(1);				
			}
			
			return Integer.valueOf(0);
		}
	}
	
	static class SignProtoProcess implements ProtoProcess<Integer> {
		public final String secret;
		public final String nonce;
		public final ThrowingSupplier<InputStream,IOException> inSupplier;
		public SignProtoProcess(String secret, String nonce, ThrowingSupplier<InputStream,IOException> inSupplier) {
			this.secret = secret;
			this.nonce = nonce;
			this.inSupplier = inSupplier;
		}
		
		@Override
		public ProcessLike<Integer> start(InputStream in, OutputStream out, OutputStream err) {
			return new SignProcess(
				secret, nonce, inSupplier,
				printStream(out),
				printStream(err)
			);
		}
	}
	
	static final Pattern NONCE_ARG_PAT = Pattern.compile("--nonce=(.*)");
	static final Pattern SECRET_ARG_PAT = Pattern.compile("--secret=(.*)");
	static ProtoProcess<Integer> parseSignMain(String[] args, int i) {
		String nonce = null;
		String secret = null;
		String inputName = null;
		List<String> errorMessages = new ArrayList<>();
		for( ; i<args.length; ++i ) {
			Matcher m;
			if( (m = NONCE_ARG_PAT.matcher(args[i])).matches() ) {
				if( nonce != null ) {
					errorMessages.add("nonce specified more than once");
				}
				nonce = m.group(1);
			} else if( (m = SECRET_ARG_PAT.matcher(args[i])).matches() ) {
				if( secret != null ) {
					errorMessages.add("secret specified more than once");
				}
				secret = m.group(1);
			} else if( "-".equals(args[i]) || !args[i].startsWith("-") ) {
				if( inputName != null ) {
					errorMessages.add("input specified more than once");
				}
				inputName = args[i];
			}
		}
		
		if( secret == null ) errorMessages.add("--secret is required");
		if( nonce == null ) errorMessages.add("--nonce is required");
		
		if( !errorMessages.isEmpty() ) {
			StringBuilder sb = new StringBuilder();
			for( String msg : errorMessages ) sb.append(msg).append('\n');
			return new PrintAndExitAction("", sb.toString(), 1);
		}
		
		String finalInputName = inputName == null ? "-" : inputName;
		ThrowingSupplier<InputStream,IOException> inSupplier = "-".equals(finalInputName)
			? () -> System.in
			: () -> new FileInputStream(finalInputName);
		
		return new SignProtoProcess(secret, nonce, inSupplier);
	}
	
	static ProtoProcess<Integer> parseMain(String[] args) {
		if( args.length == 0 ) {
			return new PrintAndExitAction("", "No command specified\n", 1);
		}
		if( "self-test".equals(args[0]) ) {
			return (in,out,err) -> new SelfTestProcess(printStream(out),printStream(err));
		}
		if( "sign".equals(args[0]) ) {
			return parseSignMain(args, 1);
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
