/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.tef;

import java.util.ArrayList;
import java.util.List;

import net.nuke24.togetl.ducer.DucerChunk;
import net.nuke24.togetl.ducer.DucerState2;
import net.nuke24.togetl.ducer.Function;
import net.nuke24.togetl.tef.LLChunks.Chunk;
import net.nuke24.togetl.tef.LLChunks.ContentPiece;
import net.nuke24.togetl.tef.LLChunks.Header;
import net.nuke24.togetl.tef.LLChunks.HeaderKey;
import net.nuke24.togetl.tef.LLChunks.HeaderValuePiece;
import net.nuke24.togetl.tef.util.ArrayUtil;

/**
 * Merges adjacent chunks and turns HeaderKeys into Headers. Derived from
 * TScript34-P0014 (MIT); spans are joined when chunks are merged.
 */
public final class LLChunkMerger implements Function<DucerChunk<Chunk[]>, DucerState2<Chunk[], Chunk[]>> {
	private static final Chunk[] EMPTY_CHUNKS = new Chunk[0];
	private static final DucerChunk<Chunk[]> EMPTY_OPEN_INPUT = new DucerChunk<Chunk[]>(EMPTY_CHUNKS, false);
	private static final DucerChunk<Chunk[]> EMPTY_OPEN_OUTPUT = new DucerChunk<Chunk[]>(EMPTY_CHUNKS, false);
	private static final DucerChunk<Chunk[]> EMPTY_CLOSED_INPUT = new DucerChunk<Chunk[]>(EMPTY_CHUNKS, true);
	
	public static final DucerState2<Chunk[], Chunk[]> FULL =
			new DucerState2<Chunk[], Chunk[]>(
					new LLChunkMerger(null, true), EMPTY_OPEN_INPUT, EMPTY_OPEN_OUTPUT);
					
	public static final DucerState2<Chunk[], Chunk[]> HEADERS_ONLY =
			new DucerState2<Chunk[], Chunk[]>(
					new LLChunkMerger(null, false), EMPTY_OPEN_INPUT, EMPTY_OPEN_OUTPUT);
					
	private final Chunk previousChunk;
	private final boolean mergeContentChunks;
	
	private LLChunkMerger(Chunk previousChunk, boolean mergeContentChunks) {
		this.previousChunk = previousChunk;
		this.mergeContentChunks = mergeContentChunks;
	}
	
	private Chunk merge(Chunk a, Chunk b) {
		if (a instanceof Header && b instanceof HeaderValuePiece) {
			return new Header(
					((Header) a).key,
					((Header) a).value + ((HeaderValuePiece) b).data,
					TEFSpan.join(a.getSpan(), b.getSpan()));
		}
		if (a instanceof HeaderValuePiece && b instanceof HeaderValuePiece) {
			return new HeaderValuePiece(
					((HeaderValuePiece) a).data + ((HeaderValuePiece) b).data,
					TEFSpan.join(a.getSpan(), b.getSpan()));
		}
		if (mergeContentChunks && a instanceof ContentPiece && b instanceof ContentPiece) {
			return new ContentPiece(
					ArrayUtil.concat(((ContentPiece) a).data, 0, ((ContentPiece) b).data),
					TEFSpan.join(a.getSpan(), b.getSpan()));
		}
		return null;
	}
	
	@Override
	public DucerState2<Chunk[], Chunk[]> apply(DucerChunk<Chunk[]> input) {
		Chunk previous = this.previousChunk;
		List<Chunk> merged = new ArrayList<Chunk>();
		for (Chunk chunk : input.payload) {
			if (chunk instanceof HeaderValuePiece && ((HeaderValuePiece) chunk).data.isEmpty()) {
				continue;
			}
			if (chunk instanceof ContentPiece && ((ContentPiece) chunk).data.length == 0) {
				continue;
			}
			if (chunk instanceof HeaderKey) {
				HeaderKey key = (HeaderKey) chunk;
				chunk = new Header(key.key, "", key.getSpan());
			}
			
			if (previous == null) {
				previous = chunk;
				continue;
			}
			
			Chunk mergedChunk = merge(previous, chunk);
			if (mergedChunk == null) {
				merged.add(previous);
				previous = chunk;
			} else {
				previous = mergedChunk;
			}
		}
		if (input.isEnd() && previous != null) {
			merged.add(previous);
			previous = null;
		}
		return new DucerState2<Chunk[], Chunk[]>(
				new LLChunkMerger(previous, mergeContentChunks),
				input.isEnd() ? EMPTY_CLOSED_INPUT : EMPTY_OPEN_INPUT,
				new DucerChunk<Chunk[]>(merged.toArray(new Chunk[merged.size()]), input.isEnd()));
	}
}
