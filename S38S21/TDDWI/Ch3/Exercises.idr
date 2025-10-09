-- 3.2.1 - Refining the type of allLengths

module S38S21.TDDWI.Ch3.Exercises

import Data.Vect

allLengths : Vect len String -> Vect len Nat
allLengths [] = []
allLengths (x :: xs) = length x :: allLengths xs
