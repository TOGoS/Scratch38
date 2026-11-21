import { OperationSpec, parseOperationSpec } from "./operation-syntax.ts";

/** Placeholder until the 3DObject type exists. */
export type StackValue = unknown;

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
 */
export async function runOperations(args: string[]): Promise<void> {
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
}
