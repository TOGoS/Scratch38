/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.tef;

import java.util.Arrays;
import java.util.Objects;

/**
 * Low-level TEF chunks. Derived from TScript34-P0014 (MIT), extended with
 * source spans, comments, and directives.
 */
public final class LLChunks {
	private LLChunks() {
	}
	
	public interface Chunk {
		TEFSpan getSpan();
	}
	
	public static final class NewEntryLine implements Chunk {
		public final String typeString;
		public final String idString;
		private final TEFSpan span;
		
		public NewEntryLine(String typeString, String idString, TEFSpan span) {
			this.typeString = typeString == null ? "" : typeString;
			this.idString = idString == null ? "" : idString;
			this.span = span;
		}
		
		public static NewEntryLine of(String typeString, String idString, TEFSpan span) {
			return new NewEntryLine(typeString, idString, span);
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "NewEntryLine[typeString=" + quote(typeString) + ", idString=" + quote(idString) + "]";
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof NewEntryLine)) return false;
			NewEntryLine that = (NewEntryLine) other;
			return typeString.equals(that.typeString) && idString.equals(that.idString);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(typeString, idString);
		}
	}
	
	public static final class Header implements Chunk {
		public final String key;
		public final String value;
		private final TEFSpan span;
		
		public Header(String key, String value, TEFSpan span) {
			this.key = key;
			this.value = value;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "Header[key=" + quote(key) + ", value=" + quote(value) + "]";
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Header)) return false;
			Header that = (Header) other;
			return key.equals(that.key) && value.equals(that.value);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(key, value);
		}
	}
	
	public static final class HeaderKey implements Chunk {
		public final String key;
		private final TEFSpan span;
		
		public HeaderKey(String key, TEFSpan span) {
			this.key = key;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "HeaderKey[key=" + quote(key) + "]";
		}
	}
	
	public static final class HeaderValuePiece implements Chunk {
		public final String data;
		private final TEFSpan span;
		
		public HeaderValuePiece(String data, TEFSpan span) {
			this.data = data;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "HeaderValuePiece[data=" + quote(data) + "]";
		}
	}
	
	public static final class ContentPiece implements Chunk {
		public final byte[] data;
		private final TEFSpan span;
		
		public ContentPiece(byte[] data, TEFSpan span) {
			this.data = data;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "ContentPiece[data=" + (data == null ? "null" : data.length + " bytes") + "]";
		}
		
		@Override
		public boolean equals(Object other) {
			return other instanceof ContentPiece && Arrays.equals(data, ((ContentPiece) other).data);
		}
		
		@Override
		public int hashCode() {
			return Arrays.hashCode(data);
		}
	}
	
	public static final class Comment implements Chunk {
		public final String text;
		private final TEFSpan span;
		
		public Comment(String text, TEFSpan span) {
			this.text = text;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "Comment[text=" + quote(text) + "]";
		}
	}
	
	public static final class Directive implements Chunk {
		public final String name;
		public final String value;
		public final String raw;
		private final TEFSpan span;
		
		public Directive(String name, String value, String raw, TEFSpan span) {
			this.name = name;
			this.value = value;
			this.raw = raw;
			this.span = span;
		}
		
		public static Directive parse(String raw, TEFSpan span) {
			int i = 1;
			while (i < raw.length() && !Character.isWhitespace(raw.charAt(i))) {
				i++;
			}
			String name = raw.substring(1, i);
			String value = i < raw.length() ? raw.substring(i).trim() : "";
			return new Directive(name, value, raw, span);
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "Directive[name=" + quote(name) + ", value=" + quote(value) + "]";
		}
	}
	
	public static final class SyntaxError implements Chunk {
		public final int lineNum;
		public final String message;
		private final TEFSpan span;
		
		public SyntaxError(int lineNum, String message, TEFSpan span) {
			this.lineNum = lineNum;
			this.message = message;
			this.span = span;
		}
		
		@Override
		public TEFSpan getSpan() {
			return span;
		}
		
		@Override
		public String toString() {
			return "SyntaxError[lineNum=" + lineNum + ", message=" + quote(message) + "]";
		}
	}
	
	/**
	 * A '#' line is a comment when it is "#" alone, "# " (hash + whitespace),
	 * or "#!" (shebang style). Anything else is a SharpFormat directive.
	 */
	public static boolean isCommentLine(String text) {
		if (text.isEmpty() || text.charAt(0) != '#') return false;
		if (text.length() == 1) return true;
		char second = text.charAt(1);
		return second == '!' || Character.isWhitespace(second);
	}
	
	static String quote(String value) {
		if (value == null) return "null";
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
