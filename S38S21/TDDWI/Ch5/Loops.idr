module S38S21.TDDWI.Ch5.Loops

import System

countdown : Nat -> IO ()
countdown 0 = putStrLn "Zero!!!"
countdown (S next) = do
	putStrLn (show (S next))
	usleep 1000000
	countdown next

main : IO ()
main = countdown 5
