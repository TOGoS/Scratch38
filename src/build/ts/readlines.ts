export async function* toLines(input: AsyncIterable<Uint8Array>): AsyncIterable<string> {
	const decoder = new TextDecoder();
	let leftover = "";

	for await (const chunk of input) {
		const text = leftover + decoder.decode(chunk);
		const lines = text.split("\n");
		
		leftover = lines.pop() || "";
		
		for (const line of lines) {
			yield line;
		}
	}
	
	if (leftover) {
		yield leftover;
	}
}
