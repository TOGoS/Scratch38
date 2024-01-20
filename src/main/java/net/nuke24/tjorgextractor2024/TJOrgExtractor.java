package net.nuke24.tjorgextractor2024;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TJOrgExtractor {
	interface ThrowingSupplier<T,E extends Throwable> {
		T get() throws E;
	}
	
	interface Consumer<T> {
		void accept(T t);
	}
	
	interface Function<I,O> {
		O apply(I input);
	}
	
	static class Recordish {
		final Map<String,Object> attributes;
		public Recordish(Map<String,Object> attrs) {
			this.attributes = Collections.unmodifiableMap(attrs);
		}
	}
	
	static final class SourceSpan {
		public final String filename;
		public final int lineIndex;
		public final int columnIndex;
		public final int endLineIndex;
		public final int endColumnIndex;
		public SourceSpan(String filename, int lineIndex, int columnIndex, int endLineIndex, int endColumnIndex) {
			this.filename = filename;
			this.lineIndex = lineIndex;
			this.columnIndex = columnIndex;
			this.endLineIndex = endLineIndex;
			this.endColumnIndex = endColumnIndex;
		}
		@Override public String toString() {
			return this.filename+":"+(this.lineIndex+1)+","+(this.columnIndex+1)+"-"+(this.endLineIndex+1)+","+(this.endColumnIndex+1);
		}
	}
	
	static final class SourceChunk {
		public final SourceSpan location;
		public final String text;
		public SourceChunk(SourceSpan location, String text) {
			this.location = location;
			this.text = text;
		}
		@Override public String toString() {
			return this.text + " (at " + this.location+ ")";
		}
	}
	
	// I wanted to try treating files as linked lists of lines.
	// For this use case it is probably pointless,
	// since I'm just going to iterate over the lines anyway.
	
	interface LinkedList<T> extends Iterable<T> {
		boolean isEmpty();
		T getHead();
		LinkedList<T> getTail();
		
		default Iterator<T> iterator() {
			final LinkedList<T> initial = this;
			return new Iterator<T>() {
				LinkedList<T> current = initial;
				
				@Override public boolean hasNext() {
					return !current.isEmpty();
				}
				@Override public T next() {
					T v = this.current.getHead();
					this.current = this.current.getTail();
					return v;
				}
			};
		}
	}
	
	static <T> T throwOrReturn(Throwable t, T v) {
		if( t == null ) return v;
		if( t instanceof RuntimeException ) throw (RuntimeException)t;
		throw new RuntimeException(t);
	}
	
	static class LazyReadingLinkedList<T> implements LinkedList<T> {
		protected ThrowingSupplier<T,?> reader;
		protected boolean done;
		protected Throwable except;
		protected T headValue;
		LazyReadingLinkedList(ThrowingSupplier<T,?> reader) {
			this.reader = reader;
		}
		
		protected synchronized void readHead() {
			if( this.done ) return;
			
			try {
				this.headValue = this.reader.get();
			} catch( Throwable t ) {
				this.except = t;
				throwOrReturn(t, null);
			} finally {
				this.done = true;
			}
		}
		
		@Override
		public T getHead() {
			this.readHead();
			return throwOrReturn( this.except, this.headValue );
		}
		
		@Override
		public boolean isEmpty() {
			this.readHead();
			return this.headValue == null;
		}
		
		@Override
		public LinkedList<T> getTail() {
			this.readHead();
			return new LazyReadingLinkedList<T>(this.reader);
		}
	}

	public static <T,R> LinkedList<R> map(LinkedList<T> inputList, Function<T,R> mapper) {
		return new LinkedList<R>() {
			@Override public boolean isEmpty() {
				return inputList.isEmpty();
			}
			@Override public R getHead() {
				return mapper.apply(inputList.getHead());
			}
			@Override public LinkedList<R> getTail() {
				return map(inputList.getTail(), mapper);
			}
		};
	}
	
	static LinkedList<SourceChunk> toLazySourceLineList(final String filename, int lineIndex, LinkedList<String> lines) {
		return new LinkedList<SourceChunk>() {
			@Override public boolean isEmpty() {
				return lines.isEmpty();
			}
			@Override public SourceChunk getHead() {
				String text = lines.getHead();
				SourceSpan sLoc = new SourceSpan(filename, lineIndex, 0, lineIndex, text.length());
				return new SourceChunk(sLoc, text);
			}
			@Override public LinkedList<SourceChunk> getTail() {
				return toLazySourceLineList(filename, lineIndex+1, lines.getTail());
			}
		};
	}
	
	static LinkedList<String> toLazyLineList(BufferedReader br, boolean closeWhenDone) {
		return new LazyReadingLinkedList<String>(new ThrowingSupplier<String,IOException>() {
			@Override public String get() throws IOException {
				String line = null;
				try {
					line = br.readLine();
					return line;
				} finally {
					if( line == null && closeWhenDone ) br.close();
				}
			}
		});
	}
	
	public static LinkedList<String> fileToLazyLines(String filename) throws IOException {
		if( "-".equals(filename) ) {
			return toLazyLineList(new BufferedReader(new InputStreamReader(System.in)), false);
		} else {
			return toLazyLineList(new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename)))), true);
		}
	}
	public static LinkedList<SourceChunk> fileToLazySourceLines(String filename) throws IOException {
		return toLazySourceLineList(filename, 0, fileToLazyLines(filename));
	}
	
	public static void main(String[] args) throws IOException {
		List<String> inputFilenames = new ArrayList<String>();
		
		for( String arg : args ) {
			inputFilenames.add(arg);
		}
		
		for( String inputFilename : inputFilenames ) {
			for( SourceChunk sc : fileToLazySourceLines(inputFilename) ) {
				System.out.println(sc);
			}
		}
	}
}
