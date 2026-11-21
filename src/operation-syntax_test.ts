import { assertEquals, assertThrows } from "@std/assert";
import { parseOperationSpec } from "./operation-syntax.ts";

Deno.test("parses a bare operation name", () => {
  assertEquals(parseOperationSpec("crash"), {
    name: "crash",
    modifiers: [],
    namedParams: new Map(),
    defaultValue: undefined,
  });
});

Deno.test("parses multi-part modifiers", () => {
  const spec = parseOperationSpec("store/stl/bin:out.stl");
  assertEquals(spec.name, "store");
  assertEquals(spec.modifiers, ["stl", "bin"]);
  assertEquals(spec.defaultValue, "out.stl");
});

Deno.test("keeps everything after the first colon as the literal default value", () => {
  const spec = parseOperationSpec("load/stl:https://example.com/a;b=c:d");
  assertEquals(spec.defaultValue, "https://example.com/a;b=c:d");
  assertEquals(spec.namedParams.size, 0);
});

Deno.test("percent-decodes named parameter names and values", () => {
  const spec = parseOperationSpec("op;first%20name=a%3Bb;flag");
  assertEquals(
    spec.namedParams,
    new Map([["first name", "a;b"], ["flag", ""]]),
  );
});

Deno.test("rejects empty operation names and modifiers", () => {
  assertThrows(() => parseOperationSpec(""), Error, "Operation name is empty");
  assertThrows(
    () => parseOperationSpec(":value"),
    Error,
    "Operation name is empty",
  );
  assertThrows(() => parseOperationSpec("load/:x"), Error, "Empty modifier");
});

Deno.test("rejects duplicate named parameters", () => {
  assertThrows(
    () => parseOperationSpec("op;a=1;a=2"),
    Error,
    "given more than once",
  );
});
