import type Operation from "./Operation.ts";
import type { StackValue } from "./Operation.ts";
import type { FileFormat } from "./fileFormat.ts";
import { openResource } from "../resource.ts";
import { makeObject3d } from "../object3d.ts";
import { parseObj } from "../obj.ts";
import { parseStl } from "../stl.ts";

export default class Load implements Operation {
	private readonly format: FileFormat;
	private readonly name: string;
	
	constructor(format: FileFormat, name: string) {
		this.format = format;
		this.name = name;
	}
	
	async apply(stack: readonly StackValue[]): Promise<StackValue[]> {
		const bytes = await openResource(this.name).read();
		if (this.format.kind === "obj") {
			return [
				...stack,
				makeObject3d(parseObj(new TextDecoder().decode(bytes))),
			];
		}
		const { variant } = this.format;
		const triangles = parseStl(
			bytes,
			variant === "auto" ? undefined : variant,
		);
		return [...stack, makeObject3d([triangles])];
	}
}
