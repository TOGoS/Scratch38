/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.ducer;

/**
 * Vendored from TScript34-P0010 (MIT).
 */
public class DucerChunk<T> {
    public final T payload;
    public final boolean isEnd;

    public DucerChunk(T payload, boolean isEnd) {
        this.payload = payload;
        this.isEnd = isEnd;
    }

    public boolean isEnd() {
        return isEnd;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DucerChunk)) return false;
        DucerChunk<?> that = (DucerChunk<?>) other;
        return (payload == that.payload
                || (payload != null && that.payload != null && payload.equals(that.payload)))
                && isEnd == that.isEnd;
    }

    @Override
    public int hashCode() {
        return (payload == null ? 0 : payload.hashCode()) ^ (isEnd ? 1 : 0);
    }
}
