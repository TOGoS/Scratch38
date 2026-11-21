import { assertEquals } from "@std/assert";
import { fixBlankLines } from "./fix-blank-lines.ts";

Deno.test("blank line inside a block takes the block's indentation", () => {
	assertEquals(
		fixBlankLines("function f() {\n\tconst a = 1;\n\n\treturn a;\n}\n"),
		"function f() {\n\tconst a = 1;\n\t\n\treturn a;\n}\n",
	);
});

Deno.test("blank line takes the depth of the line before it", () => {
	assertEquals(
		fixBlankLines("a {\n\tb {\n\t\tc();\n\n\t}\n}\n"),
		"a {\n\tb {\n\t\tc();\n\t\t\n\t}\n}\n",
	);
});

Deno.test("blank lines at top level stay empty", () => {
	assertEquals(
		fixBlankLines("import x;\n\nexport const y = 1;\n"),
		"import x;\n\nexport const y = 1;\n",
	);
});

Deno.test("whitespace-only lines are normalized to the indentation", () => {
	assertEquals(
		fixBlankLines("a {\n\tb();\n  \t \n}\n"),
		"a {\n\tb();\n\t\n}\n",
	);
});

Deno.test("already-fixed text is unchanged", () => {
	const text = "a {\n\tb();\n\t\n\tc();\n}\n";
	assertEquals(fixBlankLines(text), text);
});

Deno.test("does not add indentation after the final newline", () => {
	assertEquals(fixBlankLines("a {\n\tb();\n"), "a {\n\tb();\n");
});

Deno.test("handles a file without a trailing newline", () => {
	assertEquals(
		fixBlankLines("a {\n\tb();\n\n\tc();"),
		"a {\n\tb();\n\t\n\tc();",
	);
});
