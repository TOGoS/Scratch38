package net.nuke24.togetl.dialect;

import java.util.Arrays;
import java.util.List;

/**
 * Available TEF dialects. Explicit selection is an MVP requirement; automatic
 * detection is a post-MVP idea.
 */
public final class Dialects {
    private static final List<String> NAMES = Arrays.asList(GenericTEFDialect.NAME);

    private Dialects() {
    }

    public static TEFDialect byName(String name) {
        if (GenericTEFDialect.NAME.equals(name)) {
            return new GenericTEFDialect();
        }
        throw new IllegalArgumentException(
                "Unknown dialect: " + name + " (expected one of " + NAMES + ")");
    }

    public static List<String> names() {
        return NAMES;
    }
}
