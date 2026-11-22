import type Operation from "../operation/Operation.ts";

/** Everything parsed out of an operation token: its modifiers, and its named parameter values. */
export interface OperatorArguments {
	modifiers: string[];
	namedParams: ReadonlyMap<string, string>;
}

/** The constructor of operations. Instantiating checks the arguments, so nothing runs if they are wrong. */
export default interface Operator {
	name: string;
	/** Named parameters the operator accepts. */
	params: string[];
	/** Parameter set by the `:value` form, if the operator has one. */
	defaultParam?: string;
	instantiate(args: OperatorArguments): Operation;
}
