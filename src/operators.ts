import { OperationSpec, parseOperationSpec } from "./operation-syntax.ts";
import type Operation from "./operation/Operation.ts";
import type { StackValue } from "./operation/Operation.ts";
import type Operator from "./operator/Operator.ts";
import type { OperatorArguments } from "./operator/Operator.ts";
import crash from "./operator/crash.ts";
import dup from "./operator/dup.ts";
import load from "./operator/load.ts";
import prune from "./operator/prune.ts";
import simplify from "./operator/simplify.ts";
import store from "./operator/store.ts";
import validate from "./operator/validate.ts";

const operators: Operator[] = [
	crash,
	dup,
	load,
	store,
	validate,
	simplify,
	prune,
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
