import { assertEquals, assertRejects, assertThrows } from "@std/assert";
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

Deno.test("http and https URIs are URL resources", () => {
	assertEquals(parseResourceName("http://example.com/a.stl"), {
		kind: "url",
		href: "http://example.com/a.stl",
	});
	assertEquals(parseResourceName("https://example.com/a.stl"), {
		kind: "url",
		href: "https://example.com/a.stl",
	});
});

Deno.test("other URI schemes are rejected", () => {
	assertThrows(
		() => parseResourceName("ftp://example.com/a.stl"),
		Error,
		"Unsupported URI scheme",
	);
});

Deno.test("reads http resources and reports HTTP errors", async () => {
	const server = Deno.serve(
		{ port: 0, hostname: "127.0.0.1", onListen() {} },
		(request) => {
			if (new URL(request.url).pathname === "/missing.stl") {
				return new Response("nope", { status: 404 });
			}
			return new Response(new Uint8Array([1, 2, 3]));
		},
	);
	try {
		const base = `http://127.0.0.1:${server.addr.port}`;
		assertEquals(
			await openResource(`${base}/a.stl`).read(),
			new Uint8Array([1, 2, 3]),
		);
		await assertRejects(
			() => openResource(`${base}/missing.stl`).read(),
			Error,
			"HTTP 404",
		);
	} finally {
		await server.shutdown();
	}
});

Deno.test("http resources cannot be written", async () => {
	await assertRejects(
		() => openResource("https://example.com/a.stl").write(new Uint8Array()),
		Error,
		"Cannot store to",
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
