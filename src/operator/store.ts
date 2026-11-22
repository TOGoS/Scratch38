import type Operator from "./Operator.ts";
import Store from "../operation/Store.ts";
import { requiredParam } from "./checks.ts";
import { parseFileFormat } from "./parseFormat.ts";

export const operator: Operator = {
	name: "store",
	params: ["resource-name"],
	defaultParam: "resource-name",
	instantiate({ modifiers, namedParams }) {
		const format = parseFileFormat("store", modifiers);
		const name = requiredParam("store", namedParams, "resource-name");
		return new Store(format, name);
	},
};
