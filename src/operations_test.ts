import { assertRejects } from "@std/assert";
import { runOperations } from "./operations.ts";

Deno.test("crash throws with its message", async () => {
  await assertRejects(() => runOperations(["crash:boom"]), Error, "boom");
});

Deno.test("crash without a message uses the default", async () => {
  await assertRejects(
    () => runOperations(["crash"]),
    Error,
    "Crash operation invoked",
  );
});

Deno.test("crash accepts its message as a named parameter", async () => {
  await assertRejects(
    () => runOperations(["crash;message=a%20b"]),
    Error,
    "a b",
  );
});

Deno.test("crash rejects modifiers", async () => {
  await assertRejects(
    () => runOperations(["crash/x"]),
    Error,
    "takes no modifiers",
  );
});

Deno.test("rejects unknown operations", async () => {
  await assertRejects(
    () => runOperations(["bogus"]),
    Error,
    "Unknown operation: bogus",
  );
});

Deno.test("rejects unknown parameters and default values that have no target", async () => {
  await assertRejects(
    () => runOperations(["crash;nope=1"]),
    Error,
    "does not take parameter nope",
  );
  await assertRejects(
    () => runOperations(["crash;message=1:2"]),
    Error,
    "given both",
  );
});

Deno.test("checks all operations before running any", async () => {
  await assertRejects(
    () => runOperations(["crash:first", "bogus"]),
    Error,
    "Unknown operation: bogus",
  );
});

Deno.test("requires at least one operation", async () => {
  await assertRejects(() => runOperations([]), Error, "No operations given");
});
