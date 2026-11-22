import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";

export default class Crash implements Operation {
	constructor(private readonly message: string) {}
	
	async apply(_stack: readonly StackValue[]): Promise<StackValue[]> {
		throw new Error(this.message);
	}
}
