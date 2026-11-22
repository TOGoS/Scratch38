import type Operator from "./Operator.ts";
import Simplify from "../operation/Simplify.ts";

export default {
	name: "simplify",
	params: [],
	instantiate({ modifiers }) {
		const key = modifiers.join("/");
		if (key !== "" && key !== "single-loop-polygons") {
			throw new Error(
				`simplify takes no modifiers except single-loop-polygons, got ${key}`,
			);
		}
		return new Simplify(key === "single-loop-polygons");
	},
} satisfies Operator;
