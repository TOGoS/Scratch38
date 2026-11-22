import type { Object3D } from "../object3d.ts";

export type StackValue = Object3D;

/** A concrete operation, ready to run against a stack. It returns a new stack and leaves its input alone. */
export default interface Operation {
	apply(stack: readonly StackValue[]): Promise<StackValue[]>;
}
