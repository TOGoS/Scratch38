module S38S21.TDDWI.Ch5.Guessy

import System

readNat : IO (Maybe Nat)
readNat = do
	input <- getLine
	if all isDigit (unpack input)
		then pure (Just (cast input))
		else pure Nothing

guessGame : (target : Nat) -> IO ()
guessGame target = do
	putStr "Please guess the number I'm thinking> "
	maybeGuess <- readNat
	case maybeGuess of
		Nothing => do
			putStrLn "Type a decimal number, please."
			guessGame target
		Just guess =>
			putStr "You guessed " >>
			putStr (show guess) >>
			-- 'do' syntax doesn't like my if/else if formatting,
			-- but '>>' works with it!
			if guess > target then do
				putStrLn ", which is too high!"
				putStrLn "Try again!"
				guessGame target
			else if guess < target then do
				putStrLn ", which is too low!"
				putStrLn "Try again!"
				guessGame target
			else do
				putStrLn ", which is correct!"
				putStrLn "You win!"
				putStrLn "Bye!"

main = guessGame 1023
