/**
 * Restores indentation on blank lines, which `deno fmt` removes.
 * See "Code style" in CONTRIBUTING.md.
 *
 * Each blank line gets the indentation of the nearest non-blank line before it.
 * Do not use this on files with blank lines inside multi-line template strings:
 * those blank lines are data, and would be changed.
 *
 * Usage: deno run --allow-read --allow-write tools/fix-blank-lines.ts <file or directory>...
 * Directories are searched recursively for `.ts` files.
 */

export function fixBlankLines(text: string): string {
	const trailingNewline = text.endsWith("\n");
	const body = trailingNewline ? text.slice(0, -1) : text;
	
	let indent = "";
	const fixed = body.split("\n").map((line) => {
		if (line.trim() === "") {
			return indent;
		}
		indent = /^\t*/.exec(line)![0];
		return line;
	});
	
	return fixed.join("\n") + (trailingNewline ? "\n" : "");
}

async function* listFiles(target: string): AsyncGenerator<string> {
	const info = await Deno.stat(target);
	if (!info.isDirectory) {
		yield target;
		return;
	}
	for await (const entry of Deno.readDir(target)) {
		const path = `${target}/${entry.name}`;
		if (entry.isDirectory) {
			yield* listFiles(path);
		} else if (entry.isFile && entry.name.endsWith(".ts")) {
			yield path;
		}
	}
}

if (import.meta.main) {
	if (Deno.args.length === 0) {
		console.error("usage: fix-blank-lines.ts <file or directory>...");
		Deno.exit(2);
	}
	for (const target of Deno.args) {
		for await (const path of listFiles(target)) {
			const original = await Deno.readTextFile(path);
			const fixed = fixBlankLines(original);
			if (fixed !== original) {
				// Writing an existing file keeps its permissions, such as the executable bit on stlmod.
				await Deno.writeTextFile(path, fixed);
				console.log(`fixed ${path}`);
			}
		}
	}
}
