import type Operator from "./Operator.ts";
import Simplify from "../operation/Simplify.ts";
import { noModifiers } from "./checks.ts";

export default {
	name: "simplify",
	params: [],
	instantiate({ modifiers }) {
		noModifiers("simplify", modifiers);
		return new Simplify();
	},
} satisfies Operator;
