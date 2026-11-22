export function noModifiers(operation: string, modifiers: string[]): void {
	if (modifiers.length > 0) {
		throw new Error(
			`${operation} takes no modifiers, got ${modifiers.join("/")}`,
		);
	}
}

export function requiredParam(
	operation: string,
	params: ReadonlyMap<string, string>,
	name: string,
): string {
	const value = params.get(name);
	if (value === undefined) {
		throw new Error(`${operation} needs a ${name}`);
	}
	return value;
}
