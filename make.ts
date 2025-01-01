/// <reference no-default-lib="true"/>
/// <reference lib="deno.window"/>

import Builder, { BuildContext, BuildRule } from 'https://deno.land/x/tdbuilder@0.5.14/Builder.ts';
import Logger from 'https://deno.land/x/tdbuilder@0.5.14/Logger.ts';

import bundlify from './bundlify.ts';

export class PrefixLogger implements Logger {
	public constructor(protected prefix: string, protected parent:Logger) {}

	// deno-lint-ignore no-explicit-any
	public log(thing:any, ...stuff:any[]) {
		this.parent.log(this.prefix, thing, ...stuff);	
	}
	// deno-lint-ignore no-explicit-any
	public warn(thing:any, ...stuff:any[]) {
		this.parent.warn(this.prefix, thing, ...stuff);
	}
	// deno-lint-ignore no-explicit-any
	public error(thing:any, ...stuff:any[]) {
		this.parent.error(this.prefix, thing, ...stuff);
	}
}

const buildRules : {[name:string]: BuildRule} = {
	"www/myapp.js": {
		targetType: "file",
		prereqs: ["src/main/ts"],
		invoke: function(ctx) {
			return bundlify(["./src/main/ts/myapp.ts"], "./www/myapp.js").then(() => {});
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
