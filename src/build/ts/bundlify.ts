import * as esbuild from "npm:esbuild";
import { denoPlugins } from "jsr:@luca/esbuild-deno-loader";

export function bundleToIife(entryPoints:string[], outfile:string) : Promise<void> {
	return esbuild.build({
		plugins: [...denoPlugins()],
		entryPoints: entryPoints,
		outfile: outfile,
		bundle: true,
		format: "iife",
	}).then( br => {
		if( br.errors.length > 0 ) return Promise.reject("Errors from esbuild: "+br.errors.join("\n"));
	});
}
