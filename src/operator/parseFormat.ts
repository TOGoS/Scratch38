import type { FileFormat } from "../operation/fileFormat.ts";

/**
 * Returns the file format named by the modifiers.
 * `stl` alone means "auto": load detects binary or text from the content, store writes text.
 */
export function parseFileFormat(
	operation: string,
	modifiers: string[],
): FileFormat {
	const key = modifiers.join("/");
	switch (key) {
		case "stl":
			return { kind: "stl", variant: "auto" };
		case "stl/bin":
			return { kind: "stl", variant: "binary" };
		case "stl/text":
			return { kind: "stl", variant: "text" };
		case "obj":
		case "obj/text":
			return { kind: "obj" };
		case "obj/bin":
			throw new Error(`${operation} does not support binary OBJ`);
	}
	throw new Error(
		`${operation} takes stl, stl/bin, stl/text, obj, or obj/text, got ${
			key === "" ? "no format" : key
		}`,
	);
}
