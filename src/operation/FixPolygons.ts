import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import { fixPolygons, FixTarget } from "../fixPolygons.ts";
import { popObject } from "./stack.ts";

export default class FixPolygons implements Operation {
	private readonly target: FixTarget;
	
	constructor(target: FixTarget) {
		this.target = target;
	}
	
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { rest, object } = popObject("fix-polygons", stack);
		return [...rest, fixPolygons(object, this.target)];
	}
}
