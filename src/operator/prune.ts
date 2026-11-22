import type Operator from "./Operator.ts";
import Prune from "../operation/Prune.ts";
import { noModifiers } from "./checks.ts";

export default {
	name: "prune",
	params: [],
	instantiate({ modifiers }) {
		noModifiers("prune", modifiers);
		return new Prune();
	},
} satisfies Operator;
