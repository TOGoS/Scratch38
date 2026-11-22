import type Operator from "./Operator.ts";
import Store from "../operation/Store.ts";
import { requiredParam } from "./checks.ts";
import { parseFileFormat } from "./parseFormat.ts";

export default {
	name: "store",
	params: ["resource-name"],
	defaultParam: "resource-name",
	instantiate({ modifiers, namedParams }) {
		const autofix = modifiers[modifiers.length - 1] === "autofix";
		const format = parseFileFormat(
			"store",
			autofix ? modifiers.slice(0, -1) : modifiers,
		);
		const name = requiredParam("store", namedParams, "resource-name");
		return new Store(format, name, autofix);
	},
} satisfies Operator;
