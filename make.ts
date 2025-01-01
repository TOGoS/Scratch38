import Builder, { BuildContext, BuildRule } from 'https://deno.land/x/tdbuilder@0.5.14/Builder.ts';
import Logger from 'https://deno.land/x/tdbuilder@0.5.14/Logger.ts';

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
		// TODO Find out why they removed 'bundle' and figure out what to use instead
		cmd: [Deno.execPath(),"bundle","..."]
	}
}

if( import.meta.main ) {
	const builder = new Builder({
		logger: new PrefixLogger("SG-P2 builder:", console),
		rules: buildRules,
		globalPrerequisiteNames: ["make.ts"],
		defaultTargetNames: Object.keys(buildRules).filter(targetName => /\/install$/.exec(targetName) != null),
	});
	
	Deno.exit(await builder.processCommandLine(Deno.args));
}
