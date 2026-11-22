import type { StackValue } from "./Operation.ts";

/** Splits the top object off a stack, failing if there is none. */
export function popObject(
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
