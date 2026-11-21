import { OperationSpec, parseOperationSpec } from "./operation-syntax.ts";
import { openResource } from "./resource.ts";
import { formatStlBinary, formatStlText, parseStl, StlFormat } from "./stl.ts";
import {
	checkManifold,
	pruneVector3DObject,
	trianglesFromVector3DObject,
	Vector3DObject,
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

/**
 * Returns the STL format named by the modifiers.
 * `stl` alone means "auto": load detects the format from the content, store writes text.
 */
function stlFormatModifier(
	operation: string,
	modifiers: string[],
): "auto" | StlFormat {
	const key = modifiers.join("/");
	if (key === "stl") return "auto";
	if (key === "stl/bin") return "binary";
	if (key === "stl/text") return "text";
	if (modifiers[0] !== undefined && modifiers[0] !== "stl") {
		throw new Error(
			`${operation} does not support format ${
				modifiers[0]
			} yet; only stl is supported`,
		);
	}
	throw new Error(
		`${operation} takes stl, stl/bin, or stl/text, got ${
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
			const format = stlFormatModifier("load", modifiers);
			const name = requiredParam("load", params, "resource-name");
			const triangles = parseStl(
				await openResource(name).read(),
				format === "auto" ? undefined : format,
			);
			stack.push(vector3DObjectFromTriangles(triangles));
		},
	},
	{
		name: "store",
		params: ["resource-name"],
		defaultParam: "resource-name",
		async run({ modifiers, params, stack }) {
			const format = stlFormatModifier("store", modifiers);
			const name = requiredParam("store", params, "resource-name");
			const object = stack.pop();
			if (object === undefined) {
				throw new Error(
					"store needs a 3DObject on the stack, but the stack is empty",
				);
			}
			const triangles = trianglesFromVector3DObject(object);
			const bytes = format === "binary"
				? formatStlBinary(triangles)
				: new TextEncoder().encode(formatStlText(triangles));
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
