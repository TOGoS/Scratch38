-- How to read raw data from a stream?

import System.File
import Data.Buffer
import Control.Monad.State.State
import Control.Monad.State.Interface
import Control.Monad.Identity

-----

-- 'Ducer' method: Use my hand-crafted `Ducer` type,
-- which encapsulates a step function that returns either
-- (Left) the next Ducer, or
-- (Right) the final result.

data Ducer : (i : Type) -> (o : Type) -> Type where
	Step : (i -> Either (Ducer i o) o) -> Ducer i o

processBytesWithDucer : {r : Type} -> Ducer (Maybe Bits8) r -> Int -> File -> IO (Either FileError r)
processBytesWithDucer (Step step) chunkSize file = do
	Just buf <- newBuffer chunkSize | Nothing => pure (Left FileReadError)
	go step buf
	where
		ST = (Maybe Bits8 -> Either (Ducer (Maybe Bits8) r) r)
		go : ST -> Buffer -> IO (Either FileError r)
		processBuf : ST -> Buffer -> Int -> Int -> IO (Either FileError r)
		
		processBuf step buf offset len =
			if offset == len then
				go step buf
			else do
				b <- getBits8 buf offset
				case step (Just b) of
					Left (Step nextStep) => processBuf nextStep buf (offset + 1) len
					Right rez => pure (Right rez)
		
		go step buf = do
			rr <- readBufferData file buf 0 chunkSize
			case rr of
				Left err => pure (Left err)
				Right 0 =>
					case step Nothing of
						Right x => pure (Right x)
						Left problematicDucer => pure (Left FileReadError) -- EoF reached before state machine finished
				Right n => do
					processBuf step buf 0 n

ducerMcSteppy :
	(f : (acc -> input -> Either acc rez)) ->
	(init : acc) ->
	Ducer input (Maybe rez)
ducerMcSteppy f init = Step (\input =>
	let nextS = f init input in
	case nextS of
		Left acc => Left (ducerMcSteppy f acc)
		Right rez => Right (Just rez))

-----

-- 'State' methpd: Use the predefined `State` type, in which
-- each instance includes both the next state and the result.
-- 
-- The 'State' type is actually slightly more flexible than the 'Ducer'
-- defined earlier, since this machine can produce results
-- and continue working.  That said, processBytesWithState
-- does not take advantage of this ability; it returns the result
-- as soon as the step function returns one and does not
-- continue to read input after that.

processBytesWithState : {s : Type} -> {r : Type} -> (Maybe Bits8 -> State s (Maybe r)) -> s -> Int -> File -> IO (Either FileError r)
processBytesWithState step state chunkSize file = do
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

stateyMcSteppy :
	{0 acc : Type} ->
	(acc -> input -> Either acc rez) ->
	input -> State acc (Maybe rez)
stateyMcSteppy f b = do
	acc <- get
	case f acc b of
		Left st' => do
			put st'
			pure Nothing
		Right rez => pure (Just rez)

-----

-- Take current sum, Just the next byte or Nothing to indicate end of stream,
-- and return Either (Left) the running sum or (Right) the running sum, but
-- indicating that we are done.
summy : Int -> Maybe Bits8 -> Either Int Int -- Left is intermediate, Right is done
summy sum Nothing = Right sum
summy sum (Just nextByte) = Left (sum + cast nextByte)

main : IO ()
main = do
	-- result <- processBytesWithState (stateyMcSteppy summy) 0 1024 stdin
	result <- processBytesWithDucer (ducerMcSteppy summy 0) 1024 stdin
	putStrLn (formatMcEither result)
	where
		formatMcEither : Show err => Show res => Either err res -> String
		formatMcEither (Left err) = "Error: " ++ (show err)
		formatMcEither (Right res) = "Result: " ++ (show res)
