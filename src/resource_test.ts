import { assertEquals, assertThrows } from "@std/assert";
import { openResource, parseResourceName } from "./resource.ts";

Deno.test("- means stdin or stdout", () => {
	assertEquals(parseResourceName("-"), { kind: "stdio" });
});

Deno.test("empty resource name is an error", () => {
	assertThrows(() => parseResourceName(""), Error, "Resource name is empty");
});

Deno.test("relative and absolute paths are files", () => {
	assertEquals(parseResourceName("./a.stl"), {
		kind: "file",
		path: "./a.stl",
	});
	assertEquals(parseResourceName("/tmp/a.stl"), {
		kind: "file",
		path: "/tmp/a.stl",
	});
	assertEquals(parseResourceName("a.stl"), { kind: "file", path: "a.stl" });
});

Deno.test("file: URIs become paths", () => {
	assertEquals(parseResourceName("file:///tmp/a%20b.stl"), {
		kind: "file",
		path: "/tmp/a b.stl",
	});
});

Deno.test("other URI schemes are rejected for now", () => {
	assertThrows(
		() => parseResourceName("http://example.com/a.stl"),
		Error,
		"Unsupported URI scheme",
	);
});

Deno.test("file resources can be written and read back", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const path = `${dir}/data.bin`;
		const bytes = new Uint8Array([0, 1, 2, 255]);
		await openResource(path).write(bytes);
		assertEquals(await openResource(`file://${path}`).read(), bytes);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("reading a missing file is an error", async () => {
	const dir = await Deno.makeTempDir();
	try {
		let failed = false;
		try {
			await openResource(`${dir}/missing.stl`).read();
		} catch {
			failed = true;
		}
		assertEquals(failed, true);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});
