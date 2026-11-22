import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import type { FileFormat } from "./fileFormat.ts";
import { openResource } from "../resource.ts";
import { formatObj } from "../obj.ts";
import { trianglesOfObject3d } from "../object3d.ts";
import { formatStlBinary, formatStlText } from "../stl.ts";
import { popObject } from "./stack.ts";

export default class Store implements Operation {
	private readonly format: FileFormat;
	private readonly name: string;
	
	constructor(format: FileFormat, name: string) {
		this.format = format;
		this.name = name;
	}
	
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { rest, object } = popObject("store", stack);
		let bytes: Uint8Array;
		if (this.format.kind === "obj") {
			bytes = new TextEncoder().encode(formatObj(object));
		} else {
			const triangles = trianglesOfObject3d(object);
			bytes = this.format.variant === "binary"
				? formatStlBinary(triangles)
				: new TextEncoder().encode(formatStlText(triangles));
		}
		await openResource(this.name).write(bytes);
		return rest;
	}
}
