/**
 * STL reading and writing (ASCII and binary).
 * See https://en.wikipedia.org/wiki/STL_(file_format) for the format description.
 *
 * Triangle vertexes are counter-clockwise when viewed from outside, as STL requires.
 * Facet normals stored in the file are ignored when reading.
 * They are recomputed from the winding order when writing.
 */

export type Vertex = [number, number, number];
export type Triangle = [Vertex, Vertex, Vertex];
export type StlFormat = "text" | "binary";

const BINARY_HEADER_SIZE = 84;
const BINARY_TRIANGLE_SIZE = 50;

/**
 * Parses an STL file. If `format` is not given, the format is detected:
 * a file whose size matches the triangle count in its binary header is binary,
 * and anything else is parsed as ASCII.
 */
export function parseStl(bytes: Uint8Array, format?: StlFormat): Triangle[] {
  if (format === "binary") {
    return parseBinaryStl(bytes);
  }
  if (format === "text") {
    return parseTextStl(new TextDecoder().decode(bytes));
  }
  if (looksLikeBinaryStl(bytes)) {
    return parseBinaryStl(bytes);
  }
  return parseTextStl(new TextDecoder().decode(bytes));
}

function binaryStlSize(triangleCount: number): number {
  return BINARY_HEADER_SIZE + BINARY_TRIANGLE_SIZE * triangleCount;
}

function looksLikeBinaryStl(bytes: Uint8Array): boolean {
  if (bytes.length < BINARY_HEADER_SIZE) return false;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return binaryStlSize(view.getUint32(80, true)) === bytes.length;
}

function parseBinaryStl(bytes: Uint8Array): Triangle[] {
  if (bytes.length < BINARY_HEADER_SIZE) {
    throw new Error(
      `Binary STL is ${bytes.length} bytes, shorter than its ${BINARY_HEADER_SIZE}-byte header`,
    );
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const count = view.getUint32(80, true);
  const expectedSize = binaryStlSize(count);
  if (bytes.length !== expectedSize) {
    throw new Error(
      `Binary STL header says ${count} triangles, which needs ${expectedSize} bytes, but file is ${bytes.length} bytes`,
    );
  }

  const triangles: Triangle[] = [];
  for (let i = 0; i < count; i++) {
    // Skip the 12-byte facet normal and read the three vertexes.
    const base = BINARY_HEADER_SIZE + BINARY_TRIANGLE_SIZE * i + 12;
    const vertex = (k: number): Vertex => {
      const o = base + 12 * k;
      return [
        view.getFloat32(o, true),
        view.getFloat32(o + 4, true),
        view.getFloat32(o + 8, true),
      ];
    };
    triangles.push([vertex(0), vertex(1), vertex(2)]);
  }
  return triangles;
}

function parseTextStl(text: string): Triangle[] {
  const newlineIndex = text.indexOf("\n");
  const header = (newlineIndex === -1 ? text : text.slice(0, newlineIndex))
    .trimStart();
  if (!header.startsWith("solid")) {
    throw new Error(
      `Not an STL file: expected ASCII STL to start with "solid", or a binary STL of the right size`,
    );
  }
  // The rest of the first line is an optional name, and is ignored.
  const tokens = (newlineIndex === -1 ? "" : text.slice(newlineIndex + 1))
    .split(/\s+/).filter((t) => t !== "");

  let pos = 0;
  const next = (): string => {
    if (pos >= tokens.length) throw new Error("ASCII STL ended unexpectedly");
    return tokens[pos++];
  };
  const expect = (word: string): void => {
    const token = next();
    if (token !== word) {
      throw new Error(`ASCII STL: expected "${word}", got "${token}"`);
    }
  };
  const number = (): number => {
    const token = next();
    const n = Number(token);
    if (!Number.isFinite(n)) {
      throw new Error(`ASCII STL: expected a number, got "${token}"`);
    }
    return n;
  };
  const vertex = (): Vertex => {
    expect("vertex");
    return [number(), number(), number()];
  };

  const triangles: Triangle[] = [];
  while (tokens[pos] !== "endsolid") {
    expect("facet");
    expect("normal");
    number();
    number();
    number();
    expect("outer");
    expect("loop");
    const v0 = vertex();
    const v1 = vertex();
    const v2 = vertex();
    expect("endloop");
    expect("endfacet");
    triangles.push([v0, v1, v2]);
  }
  expect("endsolid");
  return triangles;
}

/** Computes the unit normal of a triangle by the right-hand rule. */
export function facetNormal(triangle: Triangle): Vertex {
  const [a, b, c] = triangle;
  const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
  const v = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
  const n = [
    u[1] * v[2] - u[2] * v[1],
    u[2] * v[0] - u[0] * v[2],
    u[0] * v[1] - u[1] * v[0],
  ];
  const length = Math.hypot(n[0], n[1], n[2]);
  if (length === 0) return [0, 0, 0];
  return [n[0] / length, n[1] / length, n[2] / length];
}

export function formatStlText(triangles: Triangle[]): string {
  const lines = ["solid "];
  for (const triangle of triangles) {
    const [nx, ny, nz] = facetNormal(triangle);
    lines.push(
      `  facet normal ${nx.toExponential()} ${ny.toExponential()} ${nz.toExponential()}`,
    );
    lines.push("    outer loop");
    for (const [x, y, z] of triangle) {
      lines.push(
        `      vertex ${x.toExponential()} ${y.toExponential()} ${z.toExponential()}`,
      );
    }
    lines.push("    endloop");
    lines.push("  endfacet");
  }
  lines.push("endsolid ");
  return lines.join("\n") + "\n";
}

export function formatStlBinary(triangles: Triangle[]): Uint8Array {
  const bytes = new Uint8Array(binaryStlSize(triangles.length));
  const view = new DataView(bytes.buffer);
  const header = new TextEncoder().encode("stlmod binary STL");
  bytes.set(header.subarray(0, 80), 0);
  view.setUint32(80, triangles.length, true);
  triangles.forEach((triangle, i) => {
    const base = BINARY_HEADER_SIZE + BINARY_TRIANGLE_SIZE * i;
    const normal = facetNormal(triangle);
    normal.forEach((c, k) => view.setFloat32(base + 4 * k, c, true));
    triangle.forEach((vertex, j) => {
      vertex.forEach((c, k) =>
        view.setFloat32(base + 12 + 12 * j + 4 * k, c, true)
      );
    });
    // The two bytes at base + 48 (attribute byte count) stay zero.
  });
  return bytes;
}
