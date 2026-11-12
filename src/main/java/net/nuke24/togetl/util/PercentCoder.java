package net.nuke24.togetl.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Percent-encoding for TOGETLTSV data: URIs.
 *
 * <p>The encoder is deliberately conservative: only RFC 3986 unreserved
 * characters and a small set of sub-delimiters are left as-is. Everything
 * else, including '%', '|', '#', ',', and whitespace, is percent-encoded.
 * That keeps literals unambiguous when they are embedded in a
 * whitespace-separated TSV line and in a pipe-separated encoding chain.</p>
 */
public final class PercentCoder {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PercentCoder() {
    }

    public static String encode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (isSafe(c)) {
                out.append((char) c);
            } else {
                out.append('%');
                out.append(HEX[c >>> 4]);
                out.append(HEX[c & 0x0F]);
            }
        }
        return out.toString();
    }

    public static String decode(String encoded) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
        int i = 0;
        while (i < encoded.length()) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) {
                    throw new IllegalArgumentException("Truncated percent escape in: " + encoded);
                }
                int hi = hexValue(encoded.charAt(i + 1));
                int lo = hexValue(encoded.charAt(i + 2));
                out.write((hi << 4) | lo);
                i += 3;
            } else if (c < 0x80) {
                out.write(c);
                i++;
            } else {
                int codePoint = encoded.codePointAt(i);
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
                i += Character.charCount(codePoint);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isSafe(int c) {
        if (c >= 'A' && c <= 'Z') return true;
        if (c >= 'a' && c <= 'z') return true;
        if (c >= '0' && c <= '9') return true;
        switch (c) {
            case '-': case '.': case '_': case '~':
            case '!': case '$': case '&': case '\'':
            case '(': case ')': case '*': case '+':
            case ';': case '=': case ':': case '@': case '/':
                return true;
            default:
                return false;
        }
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        throw new IllegalArgumentException("Invalid percent escape digit: " + c);
    }
}
