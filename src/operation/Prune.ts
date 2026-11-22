import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import { pruneObject3d } from "../object3d.ts";
import { popObject } from "./stack.ts";

export default class Prune implements Operation {
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { rest, object } = popObject("prune", stack);
		return [...rest, pruneObject3d(object)];
	}
}
