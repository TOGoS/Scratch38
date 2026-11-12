/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.ducer;

/**
 * Equivalent to java.util.function.Function. Vendored from TScript34-P0010
 * (MIT) so the build has no external parser dependency.
 */
public interface Function<T, R> {
	R apply(T value);
}
