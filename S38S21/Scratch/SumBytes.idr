-- How to read raw data from a stream?

import System.File
import Data.Buffer
import Control.Monad.State.State
import Control.Monad.State.Interface -- For stateyStep

processBytes : {s : Type} -> {r : Type} -> (Bits8 -> State s (Maybe r)) -> s -> Int -> File -> IO (Either FileError r)
processBytes step state chunkSize file = do
	Just buf <- newBuffer chunkSize | Nothing => pure (Left FileReadError)
	go state buf
	where
		go : s -> Buffer -> IO (Either FileError r)
		processBuf : s -> Buffer -> Int -> Int -> IO (Either FileError r)
		
		processBuf state buf offset len =
			if offset == len then
				go state buf
			else do
				b <- getBits8 buf offset
				let (state', res) = runState state (step b)
				case res of
					Just x => pure (Right x)
					Nothing => processBuf state' buf (offset + 1) len
		
		go state buf = do
			rr <- readBufferData file buf 0 chunkSize
			case rr of
				Left err => pure (Left err)
				Right 0 => pure (Left FileReadError) -- EoF reached before state machine finished
				Right n => do
					processBuf state buf 0 n

summy : Int -> Maybe Bits8 -> Maybe Int
summy sum Nothing         = Just sum
summy sum (Just nextByte) = Just (sum + (cast nextByte))

main : IO ()
main = do
	result <- processBytes (?stateyMcSteppy summy) 0 1024 stdin
	-- TODO: 
	-- putStrLn ("what ap" ++ (cast c) ++ " (" ++ (cast (the Int (cast c))) ++ ")")
	putStrLn "TODO: Write this program lol"
