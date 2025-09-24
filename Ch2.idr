-- Exercises from end of Type-Driven Development with Idris, Chapter 2

module Ch2

import Data.String as DS
import System.REPL


-- 2. Write a palindrome functoin of type String -> Bool
isPalindrome : String -> Bool
isPalindrome werd = (reverse werd) == werd

-- 3. Modify the function so that it's not case sensitive
isPalindromeCaseInsensitive : String -> Bool
isPalindromeCaseInsensitive werd =
	let werdl = toLower werd
	in (reverse werdl) == werdl

-- 4, 5. Modify it so string gotta be longish
isLongPalindrome : Nat -> String -> Bool
isLongPalindrome minLength werd =
	length werd >= minLength && isPalindromeCaseInsensitive werd

-- 6. Write a counts function...
wordAndCharCount : String -> (Nat,Nat)
wordAndCharCount text =
	(length (words text), length text)

-- 7. Write a top_ten function of type Ord a => List a -> List a
-- that returns the ten largest values in a list.
topTen : Ord a => List a -> List a
topTen thingies = take 10 (reverse (sort thingies))

-- 8. Write an over_length function of type Nat -> List String -> Nat that returns
-- the number of strings in the list longer than the given number of characters
overLength : Nat -> List String -> Nat
overLength len strings =
	foldl (\acc, elem => acc + if (length elem > len) then 1 else 0) 0 strings

-- 9. For each of palindrome and counts, write a complete program that prompts for an input,
-- calls the function, and prints its output
replify : Show x => (String -> x) -> IO ()
replify funk =
	-- repl ">" (\input => show (funk input))
	-- But `.` is nicer.  See https://idris-community.github.io/idris2-tutorial/Tutorial/Functions1/FunctionComposition.html
	repl ">" (show . funk)

isPalindromeRepl : IO ()
isPalindromeRepl = replify isPalindromeCaseInsensitive

wordAndCharCountRepl : IO ()
wordAndCharCountRepl = replify wordAndCharCount
