/// <reference no-default-lib="true"/>
/// <reference lib="deno.window"/>

import Builder, { BuildContext, BuildRule } from 'https://deno.land/x/tdbuilder@0.5.14/Builder.ts';

import PrefixLogger from './src/build/ts/PrefixLogger.ts';
import { bundleToIife } from './src/build/ts/bundlify.ts';

const buildRules : {[name:string]: BuildRule} = {
	"www/myapp.js": {
		targetType: "file",
		prereqs: ["src/main/ts"],
		invoke: function(ctx) {
			return bundleToIife(["./src/main/ts/myapp.ts"], "./www/myapp.js").then(() => {});
		},
	}
}

if( import.meta.main ) {
	const builder = new Builder({
		logger: new PrefixLogger("SG-P2 builder:", console),
		rules: buildRules,
		globalPrerequisiteNames: ["make.ts"],
		defaultTargetNames: ["www/myapp.js"]
	});
	
	Deno.exit(await builder.processCommandLine(Deno.args));
}
