/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.tef.util;

import net.nuke24.togetl.ducer.DucerChunk;
import net.nuke24.togetl.ducer.DucerState2;
import net.nuke24.togetl.ducer.Function;

/**
 * Vendored from TScript34-P0014 (MIT).
 */
public final class DucerUtil {
    private DucerUtil() {
    }

    private static final class ChainDucer<T0, T1, T2>
            implements Function<DucerChunk<T0>, DucerState2<T0, T2>> {
        private final DucerState2<T0, T1> state1;
        private final DucerState2<T1, T2> state2;

        ChainDucer(DucerState2<T0, T1> state1, DucerState2<T1, T2> state2) {
            this.state1 = state1;
            this.state2 = state2;
        }

        @Override
        public DucerState2<T0, T2> apply(DucerChunk<T0> input) {
            DucerState2<T0, T1> next1 = state1.processor.apply(input);
            DucerState2<T1, T2> next2 = state2.processor.apply(next1.output);
            return new DucerState2<T0, T2>(
                    new ChainDucer<T0, T1, T2>(next1, next2),
                    next1.passed,
                    next2.output);
        }

        DucerState2<T0, T2> toDucerState() {
            return new DucerState2<T0, T2>(this, state1.passed, state2.output);
        }
    }

    public static <T0, T1, T2> DucerState2<T0, T2> chain(
            DucerState2<T0, T1> state1,
            DucerState2<T1, T2> state2
    ) {
        return new ChainDucer<T0, T1, T2>(state1, state2).toDucerState();
    }
}
