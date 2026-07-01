module S38S21.Scratch.ProcNet1

%default total

public export
data ProcID = OSPID Int | InternalPID Int

public export
data Exit = Exited Int | Signaled Int -- Attempt at mirroring Unix model; might need work

public export
record ProcessIOSignature where
	constructor MkProcessIOSignature
	readsStdin   : Bool
	writesStdout : Bool
	writesStderr : Bool

public export
mergeSig : ProcessIOSignature -> ProcessIOSignature -> ProcessIOSignature
mergeSig a b = MkProcessIOSignature
	(a.readsStdin   || b.readsStdin)
	(a.writesStdout || b.writesStdout)
	(a.writesStderr || b.writesStderr)

public export
noIoSig : ProcessIOSignature
noIoSig = MkProcessIOSignature False False False

public export
stdoutIoSig : ProcessIOSignature
stdoutIoSig = MkProcessIOSignature True False False

public export
data PortDir = In | Out

public export
record Port where
	constructor MkPort
	nodeId : Nat
	dir    : PortDir
	ix     : Nat

public export
record Edge where
	constructor MkEdge
	from : Port
	to   : Port

mutual
	public export
	record Network (inSig : ProcessIOSignature) (outSig : ProcessIOSignature) where
		constructor MkNetwork
		nodes : List Node
		edge : List Edge


	data ProtoProcess : ProcessIOSignature -> Type where
		OSCommand : (argv : List String) -> ProtoProcess sig
		JustExit : (run : Unit -> Int) -> ProtoProcess noIoSig
		Net : Network inSig outSig -> ProtoProcess (mergeSig inSig outSig)

	public export
	record Node where
		constructor MkNode
		sig : ProcessIOSignature
		body : ProtoProcess sig
