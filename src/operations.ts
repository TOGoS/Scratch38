import { OperationSpec, parseOperationSpec } from "./operation-syntax.ts";
import { openResource } from "./resource.ts";
import {
	checkManifold,
	makeObject3d,
	Object3D,
	pruneObject3d,
	trianglesOfObject3d,
} from "./object3d.ts";
import { formatObj, parseObj } from "./obj.ts";
import { simplifyObject3d } from "./simplify.ts";
import { formatStlBinary, formatStlText, parseStl, StlFormat } from "./stl.ts";

export type StackValue = Object3D;

/** Everything parsed out of an operation token: its modifiers, and its named parameter values. */
export interface OperatorArguments {
	modifiers: string[];
	namedParams: ReadonlyMap<string, string>;
}

/** A concrete operation, ready to run against a stack. It returns a new stack and leaves its input alone. */
export interface Operation {
	apply(stack: readonly StackValue[]): Promise<StackValue[]>;
}

/** The constructor of operations. Instantiating checks the arguments, so nothing runs if they are wrong. */
export interface Operator {
	name: string;
	/** Named parameters the operator accepts. */
	params: string[];
	/** Parameter set by the `:value` form, if the operator has one. */
	defaultParam?: string;
	instantiate(args: OperatorArguments): Operation;
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

function noModifiers(operation: string, modifiers: string[]): void {
	if (modifiers.length > 0) {
		throw new Error(
			`${operation} takes no modifiers, got ${modifiers.join("/")}`,
		);
	}
}

/** Splits the top object off a stack, failing if there is none. */
function popObject(
	operation: string,
	stack: readonly StackValue[],
): { rest: StackValue[]; object: StackValue } {
	if (stack.length === 0) {
		throw new Error(
			`${operation} needs a 3D object on the stack, but the stack is empty`,
		);
	}
	return { rest: stack.slice(0, -1), object: stack[stack.length - 1] };
}

const operators: Operator[] = [
	{
		name: "crash",
		params: ["message"],
		defaultParam: "message",
		instantiate({ modifiers, namedParams }) {
			noModifiers("crash", modifiers);
			const message = namedParams.get("message") ??
				"Crash operation invoked";
			return {
				async apply() {
					throw new Error(message);
				},
			};
		},
	},
	{
		name: "dup",
		params: [],
		instantiate({ modifiers }) {
			noModifiers("dup", modifiers);
			return {
				async apply(stack) {
					const { object } = popObject("dup", stack);
					return [...stack, object];
				},
			};
		},
	},
	{
		name: "load",
		params: ["resource-name"],
		defaultParam: "resource-name",
		instantiate({ modifiers, namedParams }) {
			const format = formatModifier("load", modifiers);
			const name = requiredParam("load", namedParams, "resource-name");
			return {
				async apply(stack) {
					const bytes = await openResource(name).read();
					if (format.kind === "obj") {
						return [
							...stack,
							makeObject3d(
								parseObj(new TextDecoder().decode(bytes)),
							),
						];
					}
					const triangles = parseStl(
						bytes,
						format.variant === "auto" ? undefined : format.variant,
					);
					return [...stack, makeObject3d([triangles])];
				},
			};
		},
	},
	{
		name: "store",
		params: ["resource-name"],
		defaultParam: "resource-name",
		instantiate({ modifiers, namedParams }) {
			const format = formatModifier("store", modifiers);
			const name = requiredParam("store", namedParams, "resource-name");
			return {
				async apply(stack) {
					const { rest, object } = popObject("store", stack);
					let bytes: Uint8Array;
					if (format.kind === "obj") {
						bytes = new TextEncoder().encode(formatObj(object));
					} else {
						const triangles = trianglesOfObject3d(object);
						bytes = format.variant === "binary"
							? formatStlBinary(triangles)
							: new TextEncoder().encode(
								formatStlText(triangles),
							);
					}
					await openResource(name).write(bytes);
					return rest;
				},
			};
		},
	},
	{
		name: "validate",
		params: [],
		instantiate({ modifiers }) {
			noModifiers("validate", modifiers);
			return {
				async apply(stack) {
					const { object } = popObject("validate", stack);
					checkManifold(object);
					return [...stack];
				},
			};
		},
	},
	{
		name: "simplify",
		params: [],
		instantiate({ modifiers }) {
			noModifiers("simplify", modifiers);
			return {
				async apply(stack) {
					const { rest, object } = popObject("simplify", stack);
					return [...rest, simplifyObject3d(object)];
				},
			};
		},
	},
	{
		name: "prune",
		params: [],
		instantiate({ modifiers }) {
			noModifiers("prune", modifiers);
			return {
				async apply(stack) {
					const { rest, object } = popObject("prune", stack);
					return [...rest, pruneObject3d(object)];
				},
			};
		},
	},
];

const operatorsByName = new Map(operators.map((o) => [o.name, o]));

/** Works out the arguments for an operator from a parsed operation token, checking the names. */
function argumentsOf(
	operator: Operator,
	spec: OperationSpec,
): OperatorArguments {
	const namedParams = new Map(spec.namedParams);
	for (const name of namedParams.keys()) {
		if (!operator.params.includes(name)) {
			throw new Error(`${operator.name} does not take parameter ${name}`);
		}
	}
	if (spec.defaultValue !== undefined) {
		const defaultParam = operator.defaultParam;
		if (defaultParam === undefined) {
			throw new Error(`${operator.name} does not take a default value`);
		}
		if (namedParams.has(defaultParam)) {
			throw new Error(
				`${operator.name} parameter ${defaultParam} given both by name and as default value`,
			);
		}
		namedParams.set(defaultParam, spec.defaultValue);
	}
	return { modifiers: spec.modifiers, namedParams };
}

/** Finds the operator named by a parsed operation token, and instantiates it. Throws on bad arguments. */
export function instantiateOperation(spec: OperationSpec): Operation {
	const operator = operatorsByName.get(spec.name);
	if (operator === undefined) {
		throw new Error(`Unknown operation: ${spec.name}`);
	}
	return operator.instantiate(argumentsOf(operator, spec));
}

/**
 * Instantiates every operation before applying any of them,
 * so that a mistake late in the command line does not leave partial side effects.
 * Returns the stack as it is after the last operation.
 */
export async function runOperations(args: string[]): Promise<StackValue[]> {
	if (args.length === 0) {
		throw new Error("No operations given");
	}
	const operations = args.map((arg) =>
		instantiateOperation(parseOperationSpec(arg))
	);
	
	let stack: StackValue[] = [];
	for (const operation of operations) {
		stack = await operation.apply(stack);
	}
	return stack;
}
