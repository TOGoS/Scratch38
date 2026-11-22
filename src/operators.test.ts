import {
	assertEquals,
	assertRejects,
	assertStrictEquals,
	assertStringIncludes,
	assertThrows,
} from "@std/assert";
import { instantiateOperation, runOperations } from "./operators.ts";
import { parseOperationSpec } from "./operation-syntax.ts";
import {
	makeObject3d,
	makeObject3dFromPolygonLoops,
	polyhedronLeaves,
	trianglesOfObject3d,
} from "./object3d.ts";
import { formatObj } from "./obj.ts";
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

Deno.test("crash throws with its message", async () => {
	await assertRejects(() => runOperations(["crash:boom"]), Error, "boom");
});

Deno.test("crash without a message uses the default", async () => {
	await assertRejects(
		() => runOperations(["crash"]),
		Error,
		"Crash operation invoked",
	);
});

Deno.test("crash accepts its message as a named parameter", async () => {
	await assertRejects(
		() => runOperations(["crash;message=a%20b"]),
		Error,
		"a b",
	);
});

Deno.test("crash rejects modifiers", async () => {
	await assertRejects(
		() => runOperations(["crash/x"]),
		Error,
		"takes no modifiers",
	);
});

Deno.test("rejects unknown operations", async () => {
	await assertRejects(
		() => runOperations(["bogus"]),
		Error,
		"Unknown operation: bogus",
	);
});

Deno.test("rejects unknown parameters and default values that have no target", async () => {
	await assertRejects(
		() => runOperations(["crash;nope=1"]),
		Error,
		"does not take parameter nope",
	);
	await assertRejects(
		() => runOperations(["crash;message=1:2"]),
		Error,
		"given both",
	);
});

Deno.test("checks all operations before running any", async () => {
	await assertRejects(
		() => runOperations(["crash:first", "bogus"]),
		Error,
		"Unknown operation: bogus",
	);
});

Deno.test("requires at least one operation", async () => {
	await assertRejects(() => runOperations([]), Error, "No operations given");
});

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

Deno.test("dup needs an object on the stack", async () => {
	await assertRejects(() => runOperations(["dup"]), Error, "stack is empty");
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

Deno.test("load requires a format modifier", async () => {
	await assertRejects(
		() => runOperations(["load:x.stl"]),
		Error,
		"takes stl, stl/bin, stl/text, obj, or obj/text",
	);
});

Deno.test("unknown formats are rejected", async () => {
	await assertRejects(
		() => runOperations(["load/xyz:x"]),
		Error,
		"takes stl, stl/bin, stl/text, obj, or obj/text, got xyz",
	);
});

Deno.test("binary OBJ is rejected", async () => {
	await assertRejects(
		() => runOperations(["load/obj/bin:x.mod"]),
		Error,
		"does not support binary OBJ",
	);
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

Deno.test("validate needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["validate"]),
		Error,
		"stack is empty",
	);
});

Deno.test("simplify needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["simplify"]),
		Error,
		"stack is empty",
	);
});

Deno.test("simplify takes no modifiers", async () => {
	await assertRejects(
		() => runOperations(["simplify/keep-unused"]),
		Error,
		"takes no modifiers",
	);
});

Deno.test("prune needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["prune"]),
		Error,
		"stack is empty",
	);
});

Deno.test("prune takes no modifiers", async () => {
	await assertRejects(
		() => runOperations(["prune/x"]),
		Error,
		"takes no modifiers",
	);
});

Deno.test("store needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["store/stl/bin:x.stl"]),
		Error,
		"stack is empty",
	);
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

Deno.test("instantiation checks arguments before any operation is applied", () => {
	assertThrows(
		() => instantiateOperation(parseOperationSpec("crash/x")),
		Error,
		"takes no modifiers",
	);
});

Deno.test("apply returns a new stack and leaves the stack it was given alone", async () => {
	const object = makeObject3d([TETRAHEDRON]);
	const before = [object];
	const after = await instantiateOperation(parseOperationSpec("dup")).apply(
		before,
	);
	assertEquals(before.length, 1);
	assertEquals(after.length, 2);
	assertStrictEquals(after[0], object);
	assertStrictEquals(after[1], object);
});

Deno.test("fix-polygons takes single-loop, convex, or triangles", async () => {
	await assertRejects(
		() => runOperations(["fix-polygons"]),
		Error,
		"fix-polygons takes single-loop, convex, or triangles",
	);
});

Deno.test("fix-polygons rejects unknown modifiers", async () => {
	await assertRejects(
		() => runOperations(["fix-polygons/quads"]),
		Error,
		"fix-polygons takes single-loop, convex, or triangles, got quads",
	);
});

Deno.test("fix-polygons needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["fix-polygons/convex"]),
		Error,
		"stack is empty",
	);
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

Deno.test("simplify/single-loop-polygons is accepted, and other modifiers are not", async () => {
	const operation = instantiateOperation(
		parseOperationSpec("simplify/single-loop-polygons"),
	);
	assertEquals(typeof operation.apply, "function");
	assertThrows(
		() => instantiateOperation(parseOperationSpec("simplify/keep-unused")),
		Error,
		"takes no modifiers except single-loop-polygons, got keep-unused",
	);
});
