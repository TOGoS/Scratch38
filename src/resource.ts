import { fromFileUrl } from "@std/path";

export type ResourceLocation =
	| { kind: "stdio" }
	| { kind: "file"; path: string }
	| { kind: "url"; href: string };
	
/** Something that bytes can be read from and written to. */
export interface Resource {
	read(): Promise<Uint8Array>;
	write(bytes: Uint8Array): Promise<void>;
}

const URI_SCHEME = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;

/**
 * Interprets a resource name as described in "Resource Names" in README.md.
 * Supported are `file:`, `http:`, and `https:` URIs, plain file paths, and `-`.
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
		switch (url.protocol) {
			case "file:":
				return { kind: "file", path: fromFileUrl(url) };
			case "http:":
			case "https:":
				return { kind: "url", href: url.href };
			default:
				throw new Error(
					`Unsupported URI scheme ${url.protocol} in ${name}`,
				);
		}
	}
	return { kind: "file", path: name };
}

export function openResource(name: string): Resource {
	const location = parseResourceName(name);
	switch (location.kind) {
		case "stdio":
			return stdioResource;
		case "file":
			return fileResource(location.path);
		case "url":
			return urlResource(location.href);
	}
}

const stdioResource: Resource = {
	async read() {
		return new Uint8Array(
			await new Response(Deno.stdin.readable).arrayBuffer(),
		);
	},
	async write(bytes) {
		let offset = 0;
		while (offset < bytes.length) {
			offset += await Deno.stdout.write(bytes.subarray(offset));
		}
	},
};

function fileResource(path: string): Resource {
	return {
		read: () => Deno.readFile(path),
		write: (bytes) => Deno.writeFile(path, bytes),
	};
}

function urlResource(href: string): Resource {
	return {
		async read() {
			const response = await fetch(href);
			if (!response.ok) {
				throw new Error(
					`HTTP ${response.status} ${response.statusText} fetching ${href}`,
				);
			}
			return new Uint8Array(await response.arrayBuffer());
		},
		write() {
			return Promise.reject(
				new Error(
					`Cannot store to ${href}: only file paths and file: URIs can be written`,
				),
			);
		},
	};
}
