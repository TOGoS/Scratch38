-- How to read raw data from a stream?

import System.File
import Data.Buffer
import Control.Monad.State.State
import Control.Monad.State.Interface -- For stateyStep
import Control.Monad.Identity

processBytes : {s : Type} -> {r : Type} -> (Maybe Bits8 -> State s (Maybe r)) -> s -> Int -> File -> IO (Either FileError r)
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
				let (state', res) = runState state (step (Just b))
				case res of
					Just x => pure (Right x)
					Nothing => processBuf state' buf (offset + 1) len
		
		go state buf = do
			rr <- readBufferData file buf 0 chunkSize
			case rr of
				Left err => pure (Left err)
				Right 0 =>
					let (_, res) = runState state (step Nothing) in
					case res of
						Just x => pure (Right x)
						Nothing => pure (Left FileReadError) -- EoF reached before state machine finished
				Right n => do
					processBuf state buf 0 n

summy : Int -> Maybe Bits8 -> Maybe Int
summy sum Nothing = Nothing
summy sum (Just nextByte) = Just (sum + cast nextByte)

stateyMcSteppy :
	(s -> input -> Maybe s) ->
	input -> State s (Maybe s)
stateyMcSteppy f b = do
	st <- get
	case f st b of
		Just st' => do
			put st'
			pure Nothing
		Nothing => pure (Just st)

main : IO ()
main = do
	result <- processBytes (stateyMcSteppy summy) 0 1024 stdin
	putStrLn (formatMcEither result)
	where
		formatMcEither : Show err => Show res => Either err res -> String
		formatMcEither (Left err) = "Error: " ++ (show err)
		formatMcEither (Right res) = "Result: " ++ (show res)
