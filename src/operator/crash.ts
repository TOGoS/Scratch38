import type Operator from "./Operator.ts";
import Crash from "../operation/Crash.ts";
import { noModifiers } from "./checks.ts";

export const operator: Operator = {
	name: "crash",
	params: ["message"],
	defaultParam: "message",
	instantiate({ modifiers, namedParams }) {
		noModifiers("crash", modifiers);
		return new Crash(
			namedParams.get("message") ?? "Crash operation invoked",
		);
	},
};
