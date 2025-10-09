-- 3.2.1 - Refining the type of allLengths

module Ch3

import Data.Vect

allLengths : Vect len String -> Vect len Nat
allLengths [] = []
allLengths (x :: xs) = length x :: allLengths xs
