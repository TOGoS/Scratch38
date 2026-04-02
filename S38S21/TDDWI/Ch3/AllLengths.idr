module S38S21.TDDWI.Ch3.AllLengths

import Data.Vect

allLengths : Vect len String -> Vect len Nat
allLengths [] = []
allLengths (x :: xs) = length x :: allLengths xs
