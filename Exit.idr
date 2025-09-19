module Exit

import System

exitCode : Int
exitCode = 3

main : IO ()
main = do
	putStrLn ("Exiting with status " ++ (cast exitCode))
	exitWith (ExitFailure exitCode)
