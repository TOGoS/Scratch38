import { assertAlmostEquals, assertEquals, assertThrows } from "@std/assert";
import {
	facetNormal,
	formatStlBinary,
	formatStlText,
	parseStl,
	Triangle,
} from "./stl.ts";

const TRIANGLES: Triangle[] = [
	[
		[0, 0, 0],
		[1, 0, 0],
		[0, 1, 0],
	],
	[
		[0, 0, 0],
		[0, 1, 0],
		[0, 0, 1.5],
	],
];

const WIKIPEDIA_STYLE_ASCII = `solid name
facet normal 0 0 1
    outer loop
        vertex 0 0 0
        vertex 1 0 0
        vertex 0 1 0
    endloop
endfacet
endsolid name
`;

Deno.test("round-trips through ASCII STL", () => {
	const bytes = new TextEncoder().encode(formatStlText(TRIANGLES));
	assertEquals(parseStl(bytes, "text"), TRIANGLES);
});

Deno.test("round-trips through binary STL", () => {
	const bytes = formatStlBinary(TRIANGLES);
	assertEquals(bytes.length, 84 + 2 * 50);
	assertEquals(parseStl(bytes, "binary"), TRIANGLES);
});

Deno.test("detects binary STL even when the header starts with solid", () => {
	const bytes = formatStlBinary(TRIANGLES);
	new TextEncoder().encode("solid").forEach((b, i) => (bytes[i] = b));
	assertEquals(parseStl(bytes), TRIANGLES);
});

Deno.test("detects ASCII STL when no format is given", () => {
	const bytes = new TextEncoder().encode(WIKIPEDIA_STYLE_ASCII);
	assertEquals(parseStl(bytes), [[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]);
});

Deno.test("ignores the name on the solid line and the stored normal", () => {
	const bytes = new TextEncoder().encode(
		WIKIPEDIA_STYLE_ASCII.replace(
			"facet normal 0 0 1",
			"facet normal 9 9 9",
		),
	);
	assertEquals(parseStl(bytes, "text").length, 1);
});

Deno.test("facet normal follows the right-hand rule for counter-clockwise vertexes", () => {
	const [nx, ny, nz] = facetNormal(TRIANGLES[0]);
	assertAlmostEquals(nx, 0);
	assertAlmostEquals(ny, 0);
	assertAlmostEquals(nz, 1);
});

Deno.test("degenerate triangle has zero normal", () => {
	assertEquals(facetNormal([[0, 0, 0], [1, 0, 0], [2, 0, 0]]), [0, 0, 0]);
});

Deno.test("rejects ASCII text that does not start with solid", () => {
	const bytes = new TextEncoder().encode("hello world\n");
	assertThrows(() => parseStl(bytes, "text"), Error, "Not an STL file");
});

Deno.test("rejects truncated ASCII STL", () => {
	const bytes = new TextEncoder().encode(
		WIKIPEDIA_STYLE_ASCII.replace("endloop", ""),
	);
	assertThrows(() => parseStl(bytes, "text"), Error, 'expected "endloop"');
});

Deno.test("rejects binary STL whose size does not match its header", () => {
	const bytes = formatStlBinary(TRIANGLES).subarray(0, 100);
	assertThrows(
		() => parseStl(bytes, "binary"),
		Error,
		"header says 2 triangles",
	);
});
