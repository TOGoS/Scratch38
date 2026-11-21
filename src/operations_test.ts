import { assertEquals, assertRejects, assertStringIncludes } from "@std/assert";
import { runOperations } from "./operations.ts";
import { formatStlBinary, formatStlText, parseStl, Triangle } from "./stl.ts";

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
		assertEquals(stack[0].namedGroups, { default: 0 });
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

Deno.test("load, prune, and store round-trips a file in OBJ", async () => {
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
		assertStringIncludes(written, "o tri\n");
		assertStringIncludes(written, "f 1 2 3\n");
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("load needs a resource name", async () => {
	await assertRejects(
		() => runOperations(["load/stl"]),
		Error,
		"needs a resource-name",
	);
});

const TETRAHEDRON: Triangle[] = [
	[[0, 0, 0], [0, 1, 0], [1, 0, 0]],
	[[0, 0, 0], [1, 0, 0], [0, 0, 1]],
	[[0, 0, 0], [0, 0, 1], [0, 1, 0]],
	[[1, 0, 0], [0, 1, 0], [0, 0, 1]],
];

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

Deno.test("simplify accepts only the keep-unused modifier", async () => {
	await assertRejects(
		() => runOperations(["simplify/x"]),
		Error,
		"except keep-unused",
	);
});

Deno.test("prune needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["prune"]),
		Error,
		"stack is empty",
	);
});

Deno.test("prune rejects modifiers", async () => {
	await assertRejects(
		() => runOperations(["prune/x"]),
		Error,
		"takes no modifiers",
	);
});

Deno.test("load, prune, and store round-trips a file", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		const output = `${dir}/out.stl`;
		await Deno.writeTextFile(input, formatStlText(TRIANGLES));
		await runOperations([
			`load/stl:${input}`,
			"prune",
			`store/stl/text:${output}`,
		]);
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

Deno.test("store needs an object on the stack", async () => {
	await assertRejects(
		() => runOperations(["store/stl/bin:x.stl"]),
		Error,
		"stack is empty",
	);
});
