/// <reference no-default-lib="true"/>
/// <reference lib="es2023"/>
/// <reference lib="dom"/>

import { groot as greet } from "./mylib.ts";

document.getElementById("the-message")!.firstChild!.nodeValue = greet("world");
