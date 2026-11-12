/*
 * Derived from TScript34, Copyright (c) 2023 Dan Stevens.
 * Licensed under the MIT License; see NOTICE.
 */
package net.nuke24.togetl.ducer;

/**
 * Vendored from TScript34-P0010 (MIT).
 */
public class DucerState2<I, O> {
    public final Function<DucerChunk<I>, DucerState2<I, O>> processor;
    public final DucerChunk<I> passed;
    public final DucerChunk<O> output;

    public DucerState2(
            Function<DucerChunk<I>, DucerState2<I, O>> processor,
            DucerChunk<I> passed,
            DucerChunk<O> output
    ) {
        this.processor = processor;
        this.passed = passed;
        this.output = output;
    }

    public DucerState2<I, O> process(DucerChunk<I> input) {
        return processor.apply(input);
    }
}
