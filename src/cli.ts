import { runOperations } from "./operators.ts";

/** Runs the operations named in `args`. Any error is written to standard error, and the process exits with status 1. */
export async function main(args: string[]): Promise<void> {
	try {
		await runOperations(args);
	} catch (error) {
		console.error(
			`stlmod: ${error instanceof Error ? error.message : String(error)}`,
		);
		Deno.exit(1);
	}
}

if (import.meta.main) {
	await main(Deno.args);
}
