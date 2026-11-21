/**
 * A parsed operation specifier, e.g. `load/stl;name=value:default`.
 * See "Conventional Operation Names" in README.md.
 */
export interface OperationSpec {
	name: string;
	modifiers: string[];
	/** Named parameters, with names and values percent-decoded. */
	namedParams: Map<string, string>;
	/** Value given with the `:` form. Not percent-decoded. */
	defaultValue?: string;
}

export function parseOperationSpec(text: string): OperationSpec {
	const colonIndex = text.indexOf(":");
	const head = colonIndex === -1 ? text : text.slice(0, colonIndex);
	const defaultValue = colonIndex === -1
		? undefined
		: text.slice(colonIndex + 1);
		
	const [nameAndModifiers, ...paramParts] = head.split(";");
	const [name, ...modifiers] = nameAndModifiers.split("/");
	if (name === "") {
		throw new Error(`Operation name is empty in ${JSON.stringify(text)}`);
	}
	if (modifiers.some((m) => m === "")) {
		throw new Error(`Empty modifier in operation ${JSON.stringify(text)}`);
	}
	
	const namedParams = new Map<string, string>();
	for (const part of paramParts) {
		const equalsIndex = part.indexOf("=");
		const paramName = decodeURIComponent(
			equalsIndex === -1 ? part : part.slice(0, equalsIndex),
		);
		const paramValue = equalsIndex === -1
			? ""
			: decodeURIComponent(part.slice(equalsIndex + 1));
		if (paramName === "") {
			throw new Error(
				`Parameter name is empty in operation ${JSON.stringify(text)}`,
			);
		}
		if (namedParams.has(paramName)) {
			throw new Error(
				`Parameter ${paramName} given more than once in operation ${
					JSON.stringify(text)
				}`,
			);
		}
		namedParams.set(paramName, paramValue);
	}
	
	return { name, modifiers, namedParams, defaultValue };
}
