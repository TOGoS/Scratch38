/// <reference no-default-lib="true"/>
/// <reference lib="deno.window"/>

import Builder, { BuildContext, BuildRule } from 'https://deno.land/x/tdbuilder@0.5.14/Builder.ts';
import { mkParentDirs } from 'https://deno.land/x/tdbuilder@0.5.14/FSUtil.ts';

import PrefixLogger from './src/build/ts/PrefixLogger.ts';
import { bundleToIife } from './src/build/ts/bundlify.ts';
import { toLines } from './src/build/ts/readlines.ts';
import { parseTemplate } from './src/build/ts/templateupdate.ts';

async function templatify(templatePath : string, outputPath : string): Promise<void> {
	await mkParentDirs(outputPath);	
	
	const outStream = await Deno.open(outputPath, { write: true, create: true, createNew: true });
	const templateStream = await Deno.open(templatePath, { read: true });
	const readable = templateStream.readable;
	
	const encoder = new TextEncoder();
	
	try {
		for await (const chunk of parseTemplate(toLines(readable))) {
			if (chunk.type === "literal") {
				await outStream.write(encoder.encode(chunk.value));
			} else if (chunk.type === "variable") {
				if (chunk.name === "APP-JS") {
					const appJsContent = await Deno.readFile("temp/myapp.js");
					await outStream.write(appJsContent);
				} else {
					throw new Error(`Unrecognized variable: ${chunk.name}`);
				}
			}
		}
	} finally {
		outStream.close();
		readable.cancel();
	}
}

const buildRules : {[name:string]: BuildRule} = {
	"temp/myapp.js": {
		targetType: "file",
		prereqs: ["src/main/ts"],
		invoke: function(ctx : BuildContext) {
			return bundleToIife(["./src/main/ts/myapp.ts"], ctx.targetName).then(() => {});
		},
	},
	"www/myapp.html": {
		targetType: "file",
		prereqs: ["temp/myapp.js", "src/main/html/myapp.template.html"],
		invoke: async function(ctx : BuildContext) {
			await Deno.remove("temp/myapp.html", { recursive: true }).catch(() => {});
			await templatify("src/main/html/myapp.template.html", "temp/myapp.html");
			await mkParentDirs("www/myapp.html");
			await Deno.rename("temp/myapp.html", ctx.targetName);
		},
	}
}

if( import.meta.main ) {
	const builder = new Builder({
		logger: new PrefixLogger("SG-P2 builder:", console),
		rules: buildRules,
		globalPrerequisiteNames: ["make.ts", "src/build"],
		defaultTargetNames: ["www/myapp.html"]
	});
	
	Deno.exit(await builder.processCommandLine(Deno.args));
}
