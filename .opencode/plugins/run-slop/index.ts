import { Plugin } from "@opencode/plugin";

export default Plugin.define({
  id: "applymaxxing.run-slop-tool",
  setup: async (ctx) => {
    const cwd = ctx.location.directory;
    await ctx.tool.transform((tools) => {
      tools.add({
        name: "run_slop",
        description: "Run a SlopLang script with sloperator",
        input: {
          type: "object",
          properties: {
            path: { type: "string" },
          },
          required: ["path"],
          additionalProperties: false,
        },
        output: {
          type: "object",
          properties: {
            stdout: { type: "string" },
            stderr: { type: "string" },
            exitCode: { type: "number" },
          },
          required: ["stdout", "stderr", "exitCode"],
          additionalProperties: false,
        },
        options: { codemode: false },
        execute: async (input) => {
          const { path } = input as { path: string };
          const proc = Bun.spawn(
            ["uv", "run", "--no-active", "sloperator.py", path],
            {
              cwd,
              stdout: "pipe",
              stderr: "pipe",
            },
          );
          const stdout = await new Response(proc.stdout).text();
          const stderr = await new Response(proc.stderr).text();
          const exitCode = await proc.exited;
          return { output: { stdout, stderr, exitCode } };
        },
      });
    });
  },
});
