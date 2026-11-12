package net.nuke24.togetl.tef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One parsed TEF entry.
 */
public final class TEFEntry {
    private final String type;
    private final String value;
    private final TEFSpan span;
    private final List<TEFHeader> headers;
    private final String content;
    private final List<TEFComment> comments;
    private final List<TEFDirective> directives;

    public TEFEntry(
            String type,
            String value,
            TEFSpan span,
            List<TEFHeader> headers,
            String content,
            List<TEFComment> comments,
            List<TEFDirective> directives
    ) {
        this.type = type;
        this.value = value;
        this.span = span;
        this.headers = Collections.unmodifiableList(new ArrayList<TEFHeader>(headers));
        this.content = content;
        this.comments = Collections.unmodifiableList(new ArrayList<TEFComment>(comments));
        this.directives = Collections.unmodifiableList(new ArrayList<TEFDirective>(directives));
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public TEFSpan getSpan() {
        return span;
    }

    public List<TEFHeader> getHeaders() {
        return headers;
    }

    public String getContent() {
        return content;
    }

    public List<TEFComment> getComments() {
        return comments;
    }

    public List<TEFDirective> getDirectives() {
        return directives;
    }

    public TEFHeader getFirstHeader(String key) {
        for (TEFHeader header : headers) {
            if (header.getKey().equals(key)) {
                return header;
            }
        }
        return null;
    }
}
