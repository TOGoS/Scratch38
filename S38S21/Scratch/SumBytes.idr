-- How to read raw data from a stream?

import System.File
import Data.Buffer

-- Result of a step function, which can either return
-- Continue with a new internal state, or
-- Done with a final result.
data StepResult acc r = Continue acc | Done r

processBytesFromFile :
	{a : Type} -> {r : Type} ->
	(step : a -> Maybe Bits8 -> StepResult a r) ->
	(currentState : StepResult a r) -> (chunkSize : Int) -> File -> IO (Either FileError r)
processBytesFromFile step (Done res) chunkSize file = pure (Right res)
processBytesFromFile step (Continue acc) 0 file = pure (Left FileReadError) -- Can't read into zero-sized chunks!
processBytesFromFile step (Continue acc) chunkSize file = do
	Just buf <- newBuffer chunkSize | Nothing => pure (Left FileReadError)
	readAndProcess step acc buf
	where
		StepFn = (a -> Maybe Bits8 -> StepResult a r)
		readAndProcess : StepFn -> a -> Buffer -> IO (Either FileError r)
		processBuf : StepFn -> a -> Buffer -> Int -> Int -> IO (Either FileError r)
		
		processBuf step acc buf offset len =
			if offset == len then
				readAndProcess step acc buf
			else do
				b <- getBits8 buf offset
				case step acc (Just b) of
					Continue acc => processBuf step acc buf (offset + 1) len
					Done res => pure (Right res)
		
		readAndProcess step acc buf = do
			rr <- readBufferData file buf 0 chunkSize
			case rr of
				Left err => pure (Left err) 
				Right 0 =>
					case step acc Nothing of
					Continue acc => pure (Left FileReadError) -- EoF reached before state machine finished
					Done res => pure (Right res)
				Right n => do
					processBuf step acc buf 0 n

main : IO ()
main = do
	result <- processBytesFromFile sumBytesStep (Continue 0) 1024 stdin
	putStrLn (formatMcEither result)
	where
		sumBytesStep : Int -> Maybe Bits8 -> StepResult Int Int
		sumBytesStep sum Nothing = Done sum
		sumBytesStep sum (Just nextByte) = Continue (sum + cast nextByte)
		
		formatMcEither : Show err => Show res => Either err res -> String
		formatMcEither (Left err) = "Error: " ++ (show err)
		formatMcEither (Right res) = "Result: " ++ (show res)
