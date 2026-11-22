import type Operator from "./Operator.ts";
import Validate from "../operation/Validate.ts";
import { noModifiers } from "./checks.ts";

export default {
	name: "validate",
	params: [],
	instantiate({ modifiers }) {
		noModifiers("validate", modifiers);
		return new Validate();
	},
} satisfies Operator;
