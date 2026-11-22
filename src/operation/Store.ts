import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import type { FileFormat } from "./fileFormat.ts";
import { openResource } from "../resource.ts";
import { formatObj } from "../obj.ts";
import { trianglesOfObject3d } from "../object3d.ts";
import { fixPolygons } from "../fixPolygons.ts";
import { formatStlBinary, formatStlText } from "../stl.ts";
import { popObject } from "./stack.ts";

export default class Store implements Operation {
	private readonly format: FileFormat;
	private readonly name: string;
	private readonly autofix: boolean;
	
	constructor(format: FileFormat, name: string, autofix: boolean) {
		this.format = format;
		this.name = name;
		this.autofix = autofix;
	}
	
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const { rest, object } = popObject("store", stack);
		let bytes: Uint8Array;
		if (this.format.kind === "obj") {
			// OBJ faces cannot have holes, so autofix makes every polygon single-loop.
			const writable = this.autofix
				? fixPolygons(object, "single-loop")
				: object;
			bytes = new TextEncoder().encode(formatObj(writable));
		} else {
			// STL only holds triangles, so autofix triangulates everything.
			const writable = this.autofix
				? fixPolygons(object, "triangles")
				: object;
			const triangles = trianglesOfObject3d(writable);
			bytes = this.format.variant === "binary"
				? formatStlBinary(triangles)
				: new TextEncoder().encode(formatStlText(triangles));
		}
		await openResource(this.name).write(bytes);
		return rest;
	}
}
