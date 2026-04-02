-- 3.2.1 - Refining the type of allLengths

module S38S21.TDDWI.Ch3.Matrix

import Data.Vect

-- Matrix stuff

-- See https://idris2.readthedocs.io/en/latest/typedd/typedd.html#chapter-3
-- Obviously we can't make an n-long vector if we don't know what n is!
-- Apparently Idris 1 implicitly made n available at runtime, hence the book
-- not mentioning the {implicit} parameter.  Idris 2 makes you declare it:
createEmpties : {n : _} -> Vect n (Vect 0 elem)
createEmpties = replicate n []

transposeMatrixHalp : (x : Vect n elem) -> (xsTrans : Vect n (Vect len elem)) -> Vect n (Vect (S len) elem)
transposeMatrixHalp [] [] = []
-- The book seemed to think I could get the IDE to generate
-- this case for me, but I could not figure out how to make
-- the VS Code plugin do it, so I just typed it out:
transposeMatrixHalp (x :: xs) (y :: ys) = (x :: y) :: transposeMatrixHalp xs ys

-- Again, {n : _} means 'implicit parameter n; please infer the type'.
-- I suppose the type of `n` is obviously Nat because it's the first parameter to `Vect`.
transposeMatrix : {n : _} -> Vect m (Vect n elem) -> Vect n (Vect m elem)
transposeMatrix [] = createEmpties
transposeMatrix (x :: xs) =
	let xsTrans = transposeMatrix xs
	in transposeMatrixHalp x xsTrans
