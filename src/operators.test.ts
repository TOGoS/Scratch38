import {
	assertEquals,
	assertRejects,
	assertStrictEquals,
	assertThrows,
} from "@std/assert";
import { instantiateOperation, runOperations } from "./operators.ts";
import { parseOperationSpec } from "./operation-syntax.ts";
import { makeObject3d } from "./object3d.ts";
import { Triangle } from "./stl.ts";

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

Deno.test("dup needs an object on the stack", async () => {
	await assertRejects(() => runOperations(["dup"]), Error, "stack is empty");
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

Deno.test("autofix must follow a format", async () => {
	assertThrows(
		() => instantiateOperation(parseOperationSpec("store/autofix:x.obj")),
		Error,
		"takes stl, stl/bin, stl/text, obj, or obj/text, got no format",
	);
});
