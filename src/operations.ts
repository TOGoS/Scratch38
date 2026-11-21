import { OperationSpec, parseOperationSpec } from "./operation-syntax.ts";
import { openResource } from "./resource.ts";
import { formatObj, parseObj } from "./obj.ts";
import { formatStlBinary, formatStlText, parseStl, StlFormat } from "./stl.ts";
import { simplifyVector3DObject } from "./simplify.ts";
import {
	checkManifold,
	pruneVector3DObject,
	trianglesFromVector3DObject,
	Vector3DObject,
	vector3DObjectFromPolyhedrons,
	vector3DObjectFromTriangles,
} from "./vector3d-object.ts";

export type StackValue = Vector3DObject;

export interface OperationContext {
	modifiers: string[];
	params: ReadonlyMap<string, string>;
	stack: StackValue[];
}

export interface OperationDefinition {
	name: string;
	/** Named parameters the operation accepts. */
	params: string[];
	/** Parameter set by the `:value` form, if the operation has one. */
	defaultParam?: string;
	run(context: OperationContext): void | Promise<void>;
}

type FileFormat =
	| { kind: "stl"; variant: "auto" | StlFormat }
	| { kind: "obj" };
	
/**
 * Returns the file format named by the modifiers.
 * `stl` alone means "auto": load detects binary or text from the content, store writes text.
 */
function formatModifier(operation: string, modifiers: string[]): FileFormat {
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

function requiredParam(
	operation: string,
	params: ReadonlyMap<string, string>,
	name: string,
): string {
	const value = params.get(name);
	if (value === undefined) {
		throw new Error(`${operation} needs a ${name}`);
	}
	return value;
}

const definitions: OperationDefinition[] = [
	{
		name: "crash",
		params: ["message"],
		defaultParam: "message",
		run({ modifiers, params }) {
			if (modifiers.length > 0) {
				throw new Error(
					`crash takes no modifiers, got ${modifiers.join("/")}`,
				);
			}
			throw new Error(params.get("message") ?? "Crash operation invoked");
		},
	},
	{
		name: "load",
		params: ["resource-name"],
		defaultParam: "resource-name",
		async run({ modifiers, params, stack }) {
			const format = formatModifier("load", modifiers);
			const name = requiredParam("load", params, "resource-name");
			const bytes = await openResource(name).read();
			if (format.kind === "obj") {
				const objects = parseObj(new TextDecoder().decode(bytes));
				stack.push(
					vector3DObjectFromPolyhedrons(
						objects.map((o) => o.loops),
						objects.map((o) => o.name),
					),
				);
			} else {
				const triangles = parseStl(
					bytes,
					format.variant === "auto" ? undefined : format.variant,
				);
				stack.push(vector3DObjectFromTriangles(triangles));
			}
		},
	},
	{
		name: "store",
		params: ["resource-name"],
		defaultParam: "resource-name",
		async run({ modifiers, params, stack }) {
			const format = formatModifier("store", modifiers);
			const name = requiredParam("store", params, "resource-name");
			const object = stack.pop();
			if (object === undefined) {
				throw new Error(
					"store needs a 3DObject on the stack, but the stack is empty",
				);
			}
			let bytes: Uint8Array;
			if (format.kind === "obj") {
				bytes = new TextEncoder().encode(formatObj(object));
			} else {
				const triangles = trianglesFromVector3DObject(object);
				bytes = format.variant === "binary"
					? formatStlBinary(triangles)
					: new TextEncoder().encode(formatStlText(triangles));
			}
			await openResource(name).write(bytes);
		},
	},
	{
		name: "validate",
		params: [],
		run({ modifiers, stack }) {
			if (modifiers.length > 0) {
				throw new Error(
					`validate takes no modifiers, got ${modifiers.join("/")}`,
				);
			}
			const object = stack[stack.length - 1];
			if (object === undefined) {
				throw new Error(
					"validate needs a 3DObject on the stack, but the stack is empty",
				);
			}
			checkManifold(object);
		},
	},
	{
		name: "simplify",
		params: [],
		run({ modifiers, stack }) {
			const keepUnused = modifiers.join("/") === "keep-unused";
			if (modifiers.length > 0 && !keepUnused) {
				throw new Error(
					`simplify takes no modifiers except keep-unused, got ${
						modifiers.join("/")
					}`,
				);
			}
			const object = stack.pop();
			if (object === undefined) {
				throw new Error(
					"simplify needs a 3DObject on the stack, but the stack is empty",
				);
			}
			const simplified = simplifyVector3DObject(object, true);
			stack.push(
				keepUnused
					? simplified
					: pruneVector3DObject(simplified, "default"),
			);
		},
	},
	{
		name: "prune",
		params: ["main-object-name"],
		run({ modifiers, params, stack }) {
			if (modifiers.length > 0) {
				throw new Error(
					`prune takes no modifiers, got ${modifiers.join("/")}`,
				);
			}
			const object = stack.pop();
			if (object === undefined) {
				throw new Error(
					"prune needs a 3DObject on the stack, but the stack is empty",
				);
			}
			stack.push(
				pruneVector3DObject(
					object,
					params.get("main-object-name") ?? "default",
				),
			);
		},
	},
];

const definitionsByName = new Map(definitions.map((d) => [d.name, d]));

interface PreparedOperation {
	definition: OperationDefinition;
	context: Omit<OperationContext, "stack">;
}

function prepareOperation(spec: OperationSpec): PreparedOperation {
	const definition = definitionsByName.get(spec.name);
	if (definition === undefined) {
		throw new Error(`Unknown operation: ${spec.name}`);
	}
	
	const params = new Map(spec.namedParams);
	for (const name of params.keys()) {
		if (!definition.params.includes(name)) {
			throw new Error(
				`${definition.name} does not take parameter ${name}`,
			);
		}
	}
	if (spec.defaultValue !== undefined) {
		const defaultParam = definition.defaultParam;
		if (defaultParam === undefined) {
			throw new Error(`${definition.name} does not take a default value`);
		}
		if (params.has(defaultParam)) {
			throw new Error(
				`${definition.name} parameter ${defaultParam} given both by name and as default value`,
			);
		}
		params.set(defaultParam, spec.defaultValue);
	}
	
	return { definition, context: { modifiers: spec.modifiers, params } };
}

/**
 * Parses and checks every operation before running any of them,
 * so that a mistake late in the command line does not leave partial side effects.
 * Returns the stack as it is after the last operation.
 */
export async function runOperations(args: string[]): Promise<StackValue[]> {
	if (args.length === 0) {
		throw new Error("No operations given");
	}
	const prepared = args.map((arg) =>
		prepareOperation(parseOperationSpec(arg))
	);
	
	const stack: StackValue[] = [];
	for (const { definition, context } of prepared) {
		await definition.run({ ...context, stack });
	}
	return stack;
}
