import type Operator from "./Operator.ts";
import Load from "../operation/Load.ts";
import { requiredParam } from "./checks.ts";
import { parseFileFormat } from "./parseFormat.ts";

export default {
	name: "load",
	params: ["resource-name"],
	defaultParam: "resource-name",
	instantiate({ modifiers, namedParams }) {
		const format = parseFileFormat("load", modifiers);
		const name = requiredParam("load", namedParams, "resource-name");
		return new Load(format, name);
	},
} satisfies Operator;
