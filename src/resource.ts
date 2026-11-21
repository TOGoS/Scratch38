import { fromFileUrl } from "@std/path";

export type ResourceLocation = { kind: "stdio" } | {
	kind: "file";
	path: string;
};

const URI_SCHEME = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;

/**
 * Interprets a resource name as described in "Resource Names" in README.md.
 * Only `file:` URIs and plain file paths are supported so far.
 */
export function parseResourceName(name: string): ResourceLocation {
	if (name === "") {
		throw new Error("Resource name is empty");
	}
	if (name === "-") {
		return { kind: "stdio" };
	}
	if (URI_SCHEME.test(name)) {
		const url = new URL(name);
		if (url.protocol !== "file:") {
			throw new Error(
				`Unsupported URI scheme ${url.protocol} in ${name}`,
			);
		}
		return { kind: "file", path: fromFileUrl(url) };
	}
	return { kind: "file", path: name };
}

export async function readResource(name: string): Promise<Uint8Array> {
	const location = parseResourceName(name);
	if (location.kind === "stdio") {
		return new Uint8Array(
			await new Response(Deno.stdin.readable).arrayBuffer(),
		);
	}
	return await Deno.readFile(location.path);
}

export async function writeResource(
	name: string,
	bytes: Uint8Array,
): Promise<void> {
	const location = parseResourceName(name);
	if (location.kind === "file") {
		await Deno.writeFile(location.path, bytes);
		return;
	}
	let offset = 0;
	while (offset < bytes.length) {
		offset += await Deno.stdout.write(bytes.subarray(offset));
	}
}
