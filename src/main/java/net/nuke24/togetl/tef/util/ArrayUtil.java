/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.tef.util;

/**
 * Vendored from TScript34-P0014 (MIT).
 */
public final class ArrayUtil {
    private ArrayUtil() {
    }

    public static byte[] concat(byte[] a, int offsetA, byte[] b) {
        if (offsetA == a.length) return b;
        if (offsetA == 0 && b.length == 0) return a;
        byte[] result = new byte[a.length - offsetA + b.length];
        for (int i = offsetA; i < a.length; i++) {
            result[i - offsetA] = a[i];
        }
        for (int i = 0; i < b.length; i++) {
            result[i + a.length - offsetA] = b[i];
        }
        return result;
    }

    /**
     * @return the offset of needle, -1 if not found, or -2 if it might be
     *         found once more data arrives.
     */
    public static int find(byte[] haystack, int offset, byte[] needle, boolean isEnd) {
        search:
        for (int i = offset; i < haystack.length; i++) {
            for (int j = 0, k = i; j < needle.length; j++, k++) {
                if (k >= haystack.length) return isEnd ? -1 : -2;
                if (needle[j] != haystack[k]) continue search;
            }
            return i;
        }
        return -1;
    }
}
