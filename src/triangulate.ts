import type { Vertex } from "./stl.ts";

type Point2 = [number, number];

const EPSILON = 1e-9;

/** Half the sum of cross products around each loop: the vector area of a face's loops together. */
export function vectorAreaOfLoops(
	loops: readonly (readonly Vertex[])[],
): Vertex {
	const sum: Vertex = [0, 0, 0];
	for (const loop of loops) {
		loop.forEach((a, i) => {
			const b = loop[(i + 1) % loop.length];
			sum[0] += a[1] * b[2] - a[2] * b[1];
			sum[1] += a[2] * b[0] - a[0] * b[2];
			sum[2] += a[0] * b[1] - a[1] * b[0];
		});
	}
	return [sum[0] / 2, sum[1] / 2, sum[2] / 2];
}

/** Projects onto the plane perpendicular to `axis`, keeping the orientation that matches the normal's sign on that axis. */
function project(v: Vertex, axis: number): Point2 {
	if (axis === 0) return [v[1], v[2]];
	if (axis === 1) return [v[2], v[0]];
	return [v[0], v[1]];
}

function cross2(o: Point2, a: Point2, b: Point2): number {
	return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
}

function signedArea2(loop: number[], pts: Point2[]): number {
	let sum = 0;
	loop.forEach((i, k) => {
		const a = pts[i];
		const b = pts[loop[(k + 1) % loop.length]];
		sum += a[0] * b[1] - a[1] * b[0];
	});
	return sum / 2;
}

/** Whether segments p1-p2 and q1-q2 cross at a point interior to both. */
function properlyCrosses(
	p1: Point2,
	p2: Point2,
	q1: Point2,
	q2: Point2,
): boolean {
	const d1 = cross2(p1, p2, q1);
	const d2 = cross2(p1, p2, q2);
	const d3 = cross2(q1, q2, p1);
	const d4 = cross2(q1, q2, p2);
	return d1 * d2 < 0 && d3 * d4 < 0;
}

/**
 * Splits the region bounded by a face's loops into triangles. The loops are an outer boundary
 * and any holes, with the face's outward normal given by the loops' vector area. Holes are joined to the
 * outer boundary with bridges, and the result is ear-clipped. Triangles come out counter-clockwise
 * when viewed from outside, like the loops they came from.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function triangulateLoops(loops: Vertex[][]): Vertex[][] {
	const normal = vectorAreaOfLoops(loops);
	const axis = normal.reduce(
		(best, c, k) => (Math.abs(c) > Math.abs(normal[best]) ? k : best),
		0,
	);
	if (Math.abs(normal[axis]) < EPSILON) {
		throw new Error("cannot triangulate a polygon with no area");
	}
	// Projecting along an axis keeps orientation when the normal points along that axis, and reverses it otherwise.
	const sign = normal[axis] < 0 ? -1 : 1;
	
	const points: Vertex[] = [];
	const pts: Point2[] = [];
	const indexed = loops.map((loop) => {
		const indexes = loop.map((v) => {
			points.push(v);
			pts.push(project(v, axis));
			return points.length - 1;
		});
		return sign < 0 ? indexes.reverse() : indexes;
	});
	
	const outers = indexed.filter((loop) => signedArea2(loop, pts) > 0);
	if (outers.length !== 1) {
		throw new Error(
			"cannot triangulate a polygon made of several separate regions",
		);
	}
	const [outer] = outers;
	const holes = indexed.filter((loop) => loop !== outer);
	
	const edgesOf = (loop: number[]): [number, number][] =>
		loop.map((a, i) => [a, loop[(i + 1) % loop.length]]);
	const holeEdges = holes.flatMap(edgesOf);
	
	let ring = outer.slice();
	const byMaxX = [...holes].sort((a, b) =>
		Math.max(...b.map((i) => pts[i][0])) -
		Math.max(...a.map((i) => pts[i][0]))
	);
	for (const hole of byMaxX) {
		const m = hole.reduce(
			(best, i, k) => (pts[i][0] > pts[hole[best]][0] ? k : best),
			0,
		);
		const from = hole[m];
		const obstacles = [...edgesOf(ring), ...holeEdges];
		const distance = (i: number) =>
			(pts[ring[i]][0] - pts[from][0]) ** 2 +
			(pts[ring[i]][1] - pts[from][1]) ** 2;
		const candidates = ring.map((_, pos) => pos).sort((p, q) =>
			distance(p) - distance(q)
		);
		const pos = candidates.find((p) => {
			const to = ring[p];
			return obstacles.every(([c, d]) =>
				c === from || c === to || d === from || d === to ||
				!properlyCrosses(pts[from], pts[to], pts[c], pts[d])
			);
		});
		if (pos === undefined) {
			throw new Error("cannot join a hole to the outline of its polygon");
		}
		const to = ring[pos];
		const holeFromM = [...hole.slice(m), ...hole.slice(0, m)];
		ring = [
			...ring.slice(0, pos + 1),
			...holeFromM,
			from,
			to,
			...ring.slice(pos + 1),
		];
	}
	
	const triangles: Vertex[][] = [];
	const emit = (a: number, b: number, c: number) => {
		triangles.push(
			sign < 0
				? [points[c], points[b], points[a]]
				: [points[a], points[b], points[c]],
		);
	};
	const containsOtherPoint = (a: number, b: number, c: number): boolean =>
		ring.some((r) => {
			if (r === a || r === b || r === c) return false;
			const p = pts[r];
			if (
				(p[0] === pts[a][0] && p[1] === pts[a][1]) ||
				(p[0] === pts[b][0] && p[1] === pts[b][1]) ||
				(p[0] === pts[c][0] && p[1] === pts[c][1])
			) {
				return false;
			}
			return cross2(pts[a], pts[b], p) >= -EPSILON &&
				cross2(pts[b], pts[c], p) >= -EPSILON &&
				cross2(pts[c], pts[a], p) >= -EPSILON;
		});
		
	while (ring.length > 3) {
		const n = ring.length;
		let index = -1;
		let zeroArea = false;
		for (let i = 0; i < n; i++) {
			const a = ring[(i + n - 1) % n];
			const b = ring[i];
			const c = ring[(i + 1) % n];
			const turn = cross2(pts[a], pts[b], pts[c]);
			if (Math.abs(turn) < EPSILON) {
				index = i;
				zeroArea = true;
				break;
			}
			if (turn < 0 || containsOtherPoint(a, b, c)) continue;
			index = i;
			break;
		}
		if (index < 0) {
			throw new Error(
				"cannot triangulate: the outline appears to cross itself",
			);
		}
		const a = ring[(index + n - 1) % n];
		const b = ring[index];
		const c = ring[(index + 1) % n];
		if (!zeroArea) emit(a, b, c);
		ring.splice(index, 1);
	}
	if (
		ring.length === 3 &&
		Math.abs(cross2(pts[ring[0]], pts[ring[1]], pts[ring[2]])) >= EPSILON
	) {
		emit(ring[0], ring[1], ring[2]);
	}
	return triangles;
}
