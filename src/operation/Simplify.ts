import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import { simplifyObject3d } from "../simplify.ts";
import { popObject } from "./stack.ts";

export default class Simplify implements Operation {
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { rest, object } = popObject("simplify", stack);
		return [...rest, simplifyObject3d(object)];
	}
}
