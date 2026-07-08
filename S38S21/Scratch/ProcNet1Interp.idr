module S38S21.Scratch.ProcNet1Interp

import System.File
import Data.Buffer
import System
import S38S21.Scratch.ProcNet1

%unbound_implicits off

itemAt : {0 x : Type} -> (index : Nat) -> List x -> Maybe x
itemAt index [] = Nothing
itemAt 0 (a :: rest) = Just a
itemAt (S indexMinusOne) (a :: rest) = itemAt indexMinusOne rest

---- Interpret

data RuntimeError = WithDetail String

listToBuffer : List Bits8 -> IO (Either RuntimeError Buffer)
listToBuffer bytes = do
	Just buffer <- newBuffer (cast (length bytes))
		| Nothing => pure (Left (WithDetail $ "Failed to allocate buffer"))
	copyToBuffer bytes buffer 0
	pure (Right buffer)
	where
		copyToBuffer : List Bits8 -> Buffer -> Nat -> IO ()
		copyToBuffer [] buffer destOffset = pure ()
		copyToBuffer (byte :: moreBytes) buffer destOffset = do
			setBits8 buffer (cast destOffset) byte
			copyToBuffer moreBytes buffer (S destOffset)

reportErrorAndExit : RuntimeError -> IO ExitData
reportErrorAndExit (WithDetail text) = do
	putStrLn $ "Error interpreting program: " ++ text
	pure (Exited 1)

%unbound_implicits on

procProgramToIo : {0 iface : ProcessInterface} -> ProcProgram iface r -> List File -> IO (Either RuntimeError r)
procProgramToIo (Return res) files = pure $ Right res
procProgramToIo (Then progA func) files = do
  result <- procProgramToIo progA files
  case result of
    Left err => pure (Left err)
    Right v  => procProgramToIo (func v) files
procProgramToIo (Parallel x y f) files =
	pure $ Left $ WithDetail "Parallel to IO unimplemented"
procProgramToIo (ReadBytes streamIndex) files =
	pure $ Left $ WithDetail "ReadBytes to IO unimplemented"
procProgramToIo (WriteBytes (MkProcessPortRef streamIndex) dataToWrite) files =
	case itemAt streamIndex files of
		Nothing => pure $ Left $ WithDetail $ "Bad stream index " ++ (show streamIndex)
		Just outFile => do
			Right buffer <- listToBuffer dataToWrite
				| Left err => pure (Left err)
			Right _ <- writeBufferData outFile buffer 0 (cast (length dataToWrite))
				| Left (err, written) => pure $ Left $ WithDetail $
					"Error writing to file handle " ++ (show streamIndex) ++
					" after " ++ (show written) ++ " bytes written: " -- ++ (show err)
			pure $ Right ()
procProgramToIo (RunProcess protoProcess) files =
	pure $ Left $ WithDetail "RunProcess to IO unimplemented"

-- TODO: Will need more than just 'files';
-- probably environment variables and pwd and stuff, too.
protoProcessToIo : {0 iface : ProcessInterface} -> ProtoProcess iface -> List File -> IO ExitData
protoProcessToIo (OSCommand argv) files =
	reportErrorAndExit $ WithDetail "OSCommand to IO unimplemented"
protoProcessToIo (PureExit exitCode) files = pure (Exited exitCode)
protoProcessToIo (Program program) files = do
	result <- procProgramToIo program files
	case result of
		Left error => reportErrorAndExit error
		Right exitData => pure exitData
protoProcessToIo (Net net) files =
	reportErrorAndExit $ WithDetail "Net to IO unimplemented"

exitDataToSystemExitCode : ExitData -> ExitCode
exitDataToSystemExitCode (Exited 0) = ExitSuccess
exitDataToSystemExitCode (Exited code) with (choose (code == 0))
	exitDataToSystemExitCode (Exited code) | Left isZero = ExitSuccess
	exitDataToSystemExitCode (Exited code) | Right nz = ExitFailure code
exitDataToSystemExitCode (Signaled sig) = exitDataToSystemExitCode (Exited (128 + sig)) -- Eh

%unbound_implicits off

---- Demo

-- TODO: Fix to properly UTF-8 encode!
stringToBytes : String -> List Bits8
stringToBytes s = map (cast . ord) (unpack s)

-- Must be capitalized since it's used in type declarations,
-- unless `%unbound_implicits off`
helloInterface : ProcessInterface
helloInterface = MkProcessInterface [bytesOut, exitOut]

echoHelloProgram : ProcProgram helloInterface ExitData
echoHelloProgram = do
	WriteBytes (MkProcessPortRef 0) (stringToBytes "Hello, world!\n")
	pure $ Exited 0

main : IO ()
main = do
	result <- procProgramToIo echoHelloProgram [stdout]
	exitData <- case result of
		Left error => reportErrorAndExit error
		Right exitData => pure exitData
	exitWith (exitDataToSystemExitCode exitData)
