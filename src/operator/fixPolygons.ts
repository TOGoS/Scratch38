import type Operator from "./Operator.ts";
import FixPolygons from "../operation/FixPolygons.ts";
import type { FixTarget } from "../fixPolygons.ts";
import { noModifiers } from "./checks.ts";

const TARGETS = ["single-loop", "convex", "triangles"];

export default {
	name: "fix-polygons",
	params: [],
	instantiate({ modifiers }) {
		const key = modifiers.join("/");
		if (!TARGETS.includes(key)) {
			throw new Error(
				`fix-polygons takes single-loop, convex, or triangles, got ${
					key === "" ? "no modifier" : key
				}`,
			);
		}
		return new FixPolygons(key as FixTarget);
	},
} satisfies Operator;
