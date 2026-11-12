package net.nuke24.togetl.tef;

/**
 * A comment line found in a TEF header block.
 */
public final class TEFComment {
    private final String text;
    private final TEFSpan span;

    public TEFComment(String text, TEFSpan span) {
        this.text = text;
        this.span = span;
    }

    public String getText() {
        return text;
    }

    public TEFSpan getSpan() {
        return span;
    }
}
