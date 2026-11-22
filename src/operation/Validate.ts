import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import { checkManifold } from "../object3d.ts";
import { popObject } from "./stack.ts";

export default class Validate implements Operation {
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { object } = popObject("validate", stack);
		checkManifold(object);
		return [...stack];
	}
}
