import { assertEquals, assertStringIncludes } from "@std/assert";
import { fromFileUrl } from "@std/path";

const STLMOD = fromFileUrl(new URL("../stlmod", import.meta.url));

async function runStlmod(
	args: string[],
	stdin?: Uint8Array,
): Promise<Deno.CommandOutput> {
	const child = new Deno.Command(Deno.execPath(), {
		args: ["run", "--allow-read", "--allow-write", STLMOD, ...args],
		stdin: stdin ? "piped" : "null",
		stdout: "piped",
		stderr: "piped",
	}).spawn();
	if (stdin) {
		const writer = child.stdin.getWriter();
		await writer.write(stdin);
		await writer.close();
	}
	return await child.output();
}

const decoder = new TextDecoder();

const ASCII_TRIANGLE = new TextEncoder().encode(`solid t
facet normal 0 0 1
outer loop
vertex 0 0 0
vertex 1 0 0
vertex 0 1 0
endloop
endfacet
endsolid t
`);

Deno.test("crash writes its message to stderr and exits with status 1", async () => {
	const result = await runStlmod(["crash:boom"]);
	assertEquals(result.code, 1);
	assertStringIncludes(decoder.decode(result.stderr), "stlmod: boom");
	assertEquals(result.stdout.length, 0);
});

Deno.test("an unknown operation exits with status 1", async () => {
	const result = await runStlmod(["bogus"]);
	assertEquals(result.code, 1);
	assertStringIncludes(
		decoder.decode(result.stderr),
		"Unknown operation: bogus",
	);
});

Deno.test("no operations exits with status 1", async () => {
	const result = await runStlmod([]);
	assertEquals(result.code, 1);
});

Deno.test("reads STL from stdin and writes binary STL to a file", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const output = `${dir}/out.stl`;
		const result = await runStlmod([
			"load/stl:-",
			`store/stl/bin:${output}`,
		], ASCII_TRIANGLE);
		assertEquals(result.code, 0, decoder.decode(result.stderr));
		const bytes = await Deno.readFile(output);
		assertEquals(bytes.length, 84 + 50);
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});

Deno.test("writes ASCII STL to stdout", async () => {
	const dir = await Deno.makeTempDir();
	try {
		const input = `${dir}/in.stl`;
		await Deno.writeFile(input, ASCII_TRIANGLE);
		const result = await runStlmod([
			`load/stl:${input}`,
			"prune",
			"store/stl:-",
		]);
		assertEquals(result.code, 0, decoder.decode(result.stderr));
		assertStringIncludes(decoder.decode(result.stdout), "endsolid ");
	} finally {
		await Deno.remove(dir, { recursive: true });
	}
});
