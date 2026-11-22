import type { StlFormat } from "../stl.ts";

/** The file format of a `load` or `store`. `auto` means load detects binary or text, and store writes text. */
export type FileFormat =
	| { kind: "stl"; variant: "auto" | StlFormat }
	| { kind: "obj" };
