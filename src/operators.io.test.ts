import {
	assertEquals,
	assertRejects,
	assertStrictEquals,
	assertStringIncludes,
} from "@std/assert";
import { instantiateOperation, runOperations } from "./operators.ts";
import { parseOperationSpec } from "./operation-syntax.ts";
import {
	makeObject3dFromPolygonLoops,
	polyhedronLeaves,
	trianglesOfObject3d,
} from "./object3d.ts";
import { formatObj, parseObj } from "./obj.ts";
import { pocketBox } from "./testGeometry.ts";
import type { Vertex } from "./stl.ts";
import { formatStlBinary, formatStlText, parseStl, Triangle } from "./stl.ts";

const TRIANGLES: Triangle[] = [
	[[0, 0, 0], [1, 0, 0], [0, 1, 0]],
	[[0, 0, 0], [0, 1, 0], [0, 0, 1.5]],
];

// IMPLIED WINDING ORDER APPLIES HERE

const TETRAHEDRON: Triangle[] = [
	[[0, 0, 0], [0, 1, 0], [1, 0, 0]],
	[[0, 0, 0], [1, 0, 0], [0, 0, 1]],
	[[0, 0, 0], [0, 0, 1], [0, 1, 0]],
	[[1, 0, 0], [0, 1, 0], [0, 0, 1]],
];

const POCKET_OUTPUT = "store/obj/autofix";

Deno.test("dup leaves two references to the top object", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		const stack = await runOperations([`load/stl:${input}`, "dup"]);
		assertEquals(stack.length, 2);
		assertStrictEquals(stack[0], stack[1]);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load then store round-trips a file within one run", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		const output = `${dir}/out.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		const stack = await runOperations([
			`load/stl:${input}`,
			`store/stl/bin:${output}`,
		]);
		assertEquals(stack.length, 0);
		assertEquals(
			parseStl(await Deno.readFile(output), "binary"),
			TRIANGLES,
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load leaves the object on the stack", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		const stack = await runOperations([`load/stl:${input}`]);
		assertEquals(stack.length, 1);
		assertEquals(polyhedronLeaves(stack[0]).length, 1);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("store/stl writes ASCII", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		const output = `${dir}/out.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		await runOperations([`load/stl:${input}`, `store/stl:${output}`]);
		assertEquals(await Deno.readTextFile(output), formatStlText(TRIANGLES));
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load/stl detects binary input", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		const output = `${dir}/out.stl`;
		await Deno.writeFile(input, formatStlBinary(TRIANGLES));
		await runOperations([`load/stl:${input}`, `store/stl/text:${output}`]);
		assertEquals(
			parseStl(
				new TextEncoder().encode(await Deno.readTextFile(output)),
				"text",
			),
			TRIANGLES,
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load, simplify, prune, and store round-trips a file", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		const output = `${dir}/out.stl`;
		await Deno.writeTextFile(input, formatStlText(TETRAHEDRON));
		await runOperations([
			`load/stl:${input}`,
			"simplify",
			"prune",
			`store/stl/text:${output}`,
		]);
		assertEquals(
			parseStl(
				new TextEncoder().encode(await Deno.readTextFile(output)),
				"text",
			),
			TETRAHEDRON,
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load and store round-trip OBJ, writing each polyhedron as an unnamed object", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.obj`;
		const output = `${dir}/out.obj`;
		await Deno.writeTextFile(
			input,
			"o tri\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n",
		);
		await runOperations([`load/obj:${input}`, `store/obj:${output}`]);
		const written = await Deno.readTextFile(output);
		assertStringIncludes(written, "\no\n");
		assertStringIncludes(written, "f 1 2 3\n");
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("validate leaves a manifold object on the stack", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/tetra.stl`;
		await Deno.writeTextFile(input, formatStlText(TETRAHEDRON));
		const stack = await runOperations([`load/stl:${input}`, "validate"]);
		assertEquals(stack.length, 1);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("validate fails on an open surface", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/open.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		await assertRejects(
			() => runOperations([`load/stl:${input}`, "validate"]),
			Error,
			"not manifold",
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("store refuses non-triangular polygons in STL", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/square.obj`;
		await Deno.writeTextFile(
			input,
			"v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4\n",
		);
		await assertRejects(
			() =>
				runOperations([
					`load/obj:${input}`,
					`store/stl:${dir}/out.stl`,
				]),
			Error,
			"only triangles",
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("trianglesOfObject3d is used for the loaded data", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		const [object] = await runOperations([`load/stl:${input}`]);
		assertEquals(trianglesOfObject3d(object), TRIANGLES);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("fix-polygons/triangles, then store/stl, writes only triangles", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.obj`;
		const output = `${dir}/out.stl`;
		const lShape: [number, number][] = [[0, 0], [2, 0], [2, 1], [1, 1], [
			1,
			2,
		], [0, 2]];
		const bottom = lShape.map(([x, y]): Vertex => [x, y, 0]);
		const top = lShape.map(([x, y]): Vertex => [x, y, 1]);
		const polygons = [
			[top],
			[bottom.slice().reverse()],
			...lShape.map((_, i) => {
				const j = (i + 1) % lShape.length;
				return [[bottom[i], bottom[j], top[j], top[i]]];
			}),
		];
		await Deno.writeTextFile(
			input,
			formatObj(makeObject3dFromPolygonLoops([polygons])),
		);
		await runOperations([
			`load/obj:${input}`,
			"fix-polygons/triangles",
			`store/stl/text:${output}`,
		]);
		const triangles = parseStl(
			new TextEncoder().encode(await Deno.readTextFile(output)),
			"text",
		);
		assertEquals(triangles.length > 0, true);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("store/obj writes a polygon with holes only with autofix", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const object = makeObject3dFromPolygonLoops([pocketBox()]);
		await assertRejects(
			() =>
				instantiateOperation(
					parseOperationSpec(`store/obj:${dir}/plain.obj`),
				).apply([object]),
			Error,
			"cannot write a polygon with holes",
		);
		await instantiateOperation(
			parseOperationSpec(`${POCKET_OUTPUT}:${dir}/fixed.obj`),
		).apply([object]);
		const text = await Deno.readTextFile(`${dir}/fixed.obj`);
		assertEquals(
			parseObj(text).flat().length > 0,
			true,
		);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("store/stl writes only triangles with autofix", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const object = makeObject3dFromPolygonLoops([pocketBox()]);
		await assertRejects(
			() =>
				instantiateOperation(
					parseOperationSpec(`store/stl/text:${dir}/plain.stl`),
				).apply([object]),
			Error,
			"only triangles",
		);
		await instantiateOperation(
			parseOperationSpec(`store/stl/text/autofix:${dir}/fixed.stl`),
		).apply([object]);
		const triangles = parseStl(
			new TextEncoder().encode(
				await Deno.readTextFile(`${dir}/fixed.stl`),
			),
			"text",
		);
		assertEquals(triangles.length > 0, true);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});
