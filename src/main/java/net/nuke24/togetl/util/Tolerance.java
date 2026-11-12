package net.nuke24.togetl.util;

import java.util.Locale;

/**
 * How strictly an importer treats recoverable source problems.
 */
public enum Tolerance {
    /** Any problem aborts the import. */
    STRICT,
    /** Recover where possible and report a parse issue. */
    WARN,
    /** Recover and suppress reports for known cosmetic problems. */
    LENIENT;

    public static Tolerance parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("tolerance must not be null");
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        for (Tolerance tolerance : values()) {
            if (tolerance.name().equals(normalized)) {
                return tolerance;
            }
        }
        throw new IllegalArgumentException(
                "Unknown tolerance: " + text + " (expected strict, warn, or lenient)");
    }
}
