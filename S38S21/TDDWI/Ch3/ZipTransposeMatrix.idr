module S38S21.TDDWI.Ch3.ZipTransposeMatrix

import Data.Vect

transposeMatrix : {n : _} -> Vect m (Vect n elem) -> Vect n (Vect m elem)
transposeMatrix [] = replicate n []
transposeMatrix (row :: rows) =
	zipWith (::) row (transposeMatrix rows)
