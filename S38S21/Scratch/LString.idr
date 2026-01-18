module S38S21.Scratch.LString

-- data LString = 0 "" | 

import Data.String
import Data.Vect

--record LString (n : Nat) where
--	constructor MkLString
--	str : String
--	lengthProof : length (unpack str) = n

data LString : (length : Nat) -> Type where
  --NilMode? Nil : LString 0
  -- (::) : Char -> LString n -> LString (S n)
  OfString : (str : String) -> LString (length str)

charToString : Char -> String
charToString c = ?how

listLengthAppend : (xs : List Char) -> (ys : List Char) -> length xs + length ys = length (xs ++ ys)
listLengthAppend [] ys = Refl
listLengthAppend (x :: xs) ys = rewrite listLengthAppend xs ys in Refl

strToCharList : String -> List Char
strToCharList "" = []
strToCharList str = strIndex str 0 :: the (List Char) (strToCharList (strTail str))

unpackAppend : (stra : String) -> (strb : String) -> strToCharList (stra ++ strb) = strToCharList stra ++ strToCharList strb
unpackAppend stra strb = case null stra of
  True => Refl
  False =>
    let stra' = strTail stra in
    rewrite unpackAppend stra' strb in Refl


lengthAppend : (stra : String) -> (strb : String) -> length s1 + length s2 = length (s1 ++ s2)
lengthAppend stra strb =
  rewrite unpackAppend stra strb in
  rewrite listLengthAppend (unpack stra) (unpack strb) in Refl

(++) : LString la -> LString lb -> LString (la + lb)
-- (++) {la} {lb} lstra lstrb = case lstra of
--   OfString stra => case lstrb of
--     OfString strb => concatToLString (la + lb) stra strb
-- (++) {la} {lb} (OfString stra) (OfString strb) = concatToLString (la + lb) stra strb
--NilMode? (++) Nil Nil = Nil
--NilMode? (++) Nil (x : OfString whatever) = x
--NilMode? (++) (x : OfString whatever) Nil = x
-- (++) (OfString stra) (OfString strb) = concatToLString ((length stra) + (length strb)) stra strb
-- (++) (OfString stra) (OfString strb) = OfString (stra ++ strb)
(++) (OfString stra) (OfString strb) =
  rewrite lengthAppend stra strb in OfString (stra ++ strb)

-- Well at least this works!
hello : LString 6
hello = OfString "Hello!"

-- And this would not compile, woot!
-- helloWrong : LString 10
-- helloWrong = OfString "Hello!"
