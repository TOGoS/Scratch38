type Chunk = 
	| { type: "literal", value: string }
	| { type: "variable", name: string };

export async function* parseTemplate(input: AsyncIterable<string>): AsyncIterable<Chunk> {
	let buffer = "";
	let inVariable = false;
	let variableName = "";
	
	for await (const line of input) {
		if (line.startsWith("// BEGIN ")) {
			buffer += line + "\n";
			if (buffer) {
				yield { type: "literal", value: buffer };
				buffer = "";
			}
			inVariable = true;
			variableName = line.substring(9).trim();
		} else if (line.startsWith("// END ")) {
			if (inVariable && line.substring(7).trim() === variableName) {
				yield { type: "variable", name: variableName };
				inVariable = false;
				variableName = "";
			}
			buffer += line + "\n";
		} else {
			buffer += line + "\n";
		}
	}
	
	if (buffer) {
		yield { type: "literal", value: buffer };
	}
}
