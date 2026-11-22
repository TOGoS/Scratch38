import { assertEquals, assertThrows } from "@std/assert";
import { parseResourceName } from "./resource.ts";

Deno.test("- means stdin or stdout", () => {
	assertEquals(parseResourceName("-"), { kind: "stdio" });
});

Deno.test("empty resource name is an error", () => {
	assertThrows(() => parseResourceName(""), Error, "Resource name is empty");
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
