import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import { popObject } from "./stack.ts";

export default class Dup implements Operation {
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { object } = popObject("dup", stack);
		return [...stack, object];
	}
}
