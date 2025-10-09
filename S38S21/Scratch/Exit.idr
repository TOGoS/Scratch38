module S38S21.Scratch.Exit

import System

appVersion : String
appVersion = "0.3";

exitCode : Int
exitCode = 3

main : IO ()
main = do
	-- So, I based this on the code I found at https://learnxbyexample.com/idris/exit/, but:
	-- - `let _ = putStrLn ?whatever` DOESN'T COMPILE
	-- - `_ <- putStrln ?whatever` works as expected, and *does* output the text,
	--   contrary to what that web page said, which didn't make sense to me anyway.
	--   Actually I would think that `let _ = putStrLn` would actually just store the
	--   putStrLn action itself, rather than executing it, because that's how let works, right?
	_ <- putStrLn ("Exit.idr v" ++ appVersion ++ ": Exiting with status " ++ (cast exitCode))
	exitWith (ExitFailure exitCode)
