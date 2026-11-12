package net.nuke24.togetl.tef;

import java.util.Objects;

/**
 * A single TEF header, with duplicates preserved and its source span.
 */
public final class TEFHeader {
    private final String key;
    private final String value;
    private final TEFSpan span;

    public TEFHeader(String key, String value, TEFSpan span) {
        this.key = key;
        this.value = value;
        this.span = span;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public TEFSpan getSpan() {
        return span;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TEFHeader)) return false;
        TEFHeader that = (TEFHeader) other;
        return key.equals(that.key) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + ": " + value;
    }
}
