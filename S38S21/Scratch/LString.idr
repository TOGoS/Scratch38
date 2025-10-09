module S38S21.Scratch.LString

-- data LString = 0 "" | 

import Data.String
import Data.Vect

--record LString (n : Nat) where
--	constructor MkLString
--	str : String
--	lengthProof : length (unpack str) = n

data LString : (len : Nat) -> Type where
  -- Nil : LString 0
  -- (::) : Char -> LString n -> LString (S n)
  OfString : (str : String) -> LString (length str)

charToString : Char -> String
charToString c = ?how

-- (++) : LString la -> LString lb -> LString (la + lb)
-- (++) lstra lstrb = case lstra of
-- 	OfString stra => case lstrb of
-- 		OfString strb => OfString (stra ++ strb)

-- Well at least this works!
hello : LString 6
hello = OfString "Hello!"

-- And this would not compile, woot!
-- helloWrong : LString 10
-- helloWrong = OfString "Hello!"
