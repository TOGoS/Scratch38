import type Operator from "./Operator.ts";
import Dup from "../operation/Dup.ts";
import { noModifiers } from "./checks.ts";

export default {
	name: "dup",
	params: [],
	instantiate({ modifiers }) {
		noModifiers("dup", modifiers);
		return new Dup();
	},
} satisfies Operator;
