module S38S21.Scratch.ProcNet1

%default total

public export
data ProcID = OSPID Int | InternalPID Int

public export
data ExitData = Exited Int | Signaled Int -- Attempt at mirroring Unix model; might need work

-- I'd like to be able to have data channels that can carry any
-- payload type, but that complicates `Eq ChannelType`.
-- So for now there's just ByteChunk.
data ChannelType = ByteChunk | ExitEvent | UnitValue | SysReq | SysRes
Eq ChannelType where
	ByteChunk == ByteChunk = True
	ExitEvent == ExitEvent = True
	UnitValue == UnitValue = True
	SysRes    == SysRes    = True
	SysReq    == SysReq    = True
	x         == y         = False
Show ChannelType where
	show ByteChunk = "ByteChunk"
	show ExitEvent = "ExitEvent"
	show UnitValue = "UnitValue"
	show SysReq    = "SysReq"
	show SysRes    = "SysRes"

bytes : ChannelType
bytes = ByteChunk

data PortDirection = In | Out -- Into a node, and out of a node, repspectively.
Eq PortDirection where
	In  == In  = True
	Out == Out = True
	a   == b   = False

Show PortDirection where
	show In  = "in"
	show Out = "out"

invertPortDirection : PortDirection -> PortDirection
invertPortDirection In  = Out
invertPortDirection Out =  In

public export
record ProcessPort where
	constructor MkProcessPort
	direction : PortDirection
	channelType : ChannelType

bytesIn   = MkProcessPort In bytes
bytesOut  = MkProcessPort Out bytes
sysReqOut = MkProcessPort Out SysReq
sysResIn  = MkProcessPort In SysRes
exitOut   = MkProcessPort Out ExitEvent

public export
record ProcessInterface where
	constructor MkProcessInterface
	ports : List ProcessPort

public export
record ProcessPortRef (direction : PortDirection) (channelType : ChannelType) where
	constructor MkProcessPortRef
	portIndex : Nat

data NetworkPortNodeRef = NodeIndex Nat | NetworkBoundary

public export
record NetworkPortRef (direction : PortDirection) (channelType : ChannelType) where
	constructor MkNetworkPortRef
	-- Note that a network input is represented as a port with node = NetworkBoundary and direction = Out.
	-- i.e. the network's inputs appear as outputs, and outputs appear as inputs, from the perspective of its internal edges.
	nodeRef   : NetworkPortNodeRef
	portIndex : Nat

public export
record SomeNetworkPortRef where
	constructor MkSomeNetworkPortRef
	direction : PortDirection
	channelType : ChannelType
	portRef : NetworkPortRef direction channelType

public export
record NetworkEdge (channelType : ChannelType) where
	constructor MkEdge
	from : NetworkPortRef Out channelType
	to   : NetworkPortRef In channelType

public export
record SomeNetworkEdge where
	constructor MkSomeNetworkEdge
	channelType : ChannelType
	edge : NetworkEdge channelType

mutual
	data ProtoProcess : (iface : ProcessInterface) -> Type where
		-- TODO: OSCommand should have a whole environment, too.
		OSCommand : (argv : List String) -> ProtoProcess (MkProcessInterface [bytesIn, bytesOut, bytesOut, sysReqOut, sysResIn])
		PureExit : (exitCode : Int) -> ProtoProcess (MkProcessInterface [exitOut])
		Program : ProcProgram iface ExitData -> ProtoProcess iface
		Net : Network iface -> ProtoProcess iface
		-- TODO: Internal commands that can create/launch sub-processes
	
	public export
	record NetworkNode where
		constructor MkNetworkNode
		iface : ProcessInterface
		body : ProtoProcess iface
	
	-- Static representation of a process implemented as a collection of child processes with ports connected via 'edges'.
	-- A network process will live until the last child has exited,
	-- even if an ExitEvent flows to its own ExitEvent output port before that.
	public export
	record Network (tiface : ProcessInterface) where
		constructor MkNetwork
		nodes : List NetworkNode
		edges : List SomeNetworkEdge
	
	public export
	data ProcProgram : ProcessInterface -> Type -> Type where
		Return : a -> ProcProgram iface a
		Then : ProcProgram iface a -> (a -> ProcProgram iface b) -> ProcProgram iface b
		Parallel : ProcProgram iface a -> ProcProgram iface b -> (a -> b -> c) -> ProcProgram iface c
		ReadBytes : ProcessPortRef In ByteChunk -> ProcProgram iface (List Bits8)
		WriteBytes : ProcessPortRef Out ByteChunk -> List Bits8 -> ProcProgram iface ()
		RunProcess : ProtoProcess iface -> ProcProgram iface ExitData

Functor (ProcProgram iface) where
	map f program = Then program (\result => Return (f result))

-- Applicative composition models independent work:
-- evaluate both sides in parallel, then apply the resulting function.
-- Use Monad/Then when later steps depend on earlier results.
Applicative (ProcProgram iface) where
	pure = Return
	-- Applicative f means: (<*>) : f (a -> b) -> f a -> f b.
	-- i.e. pf returns a function that gets applied to the result of pa,
	-- which means you can evaluate the two in parallel, but without needing
	-- a separate operation to recombine the results.
	-- Note that (\f => \a => f a) is the identity function.
	pf <*> pa = Parallel pf pa (\f => \a => f a)

Monad (ProcProgram iface) where
	(>>=) = Then

---- Validation

data NetworkProblemDetail
	= BadNodeIndex Nat
	| BadPortIndex Nat
	| DirectionMismatch PortDirection PortDirection -- expected, actual
	| ChannelTypeMismatch ChannelType ChannelType   -- expected, actual

Show NetworkProblemDetail where
	show (BadNodeIndex n) = "bad node index " ++ (show n)
	show (BadPortIndex n) = "bad port index " ++ (show n)
	show (DirectionMismatch expected actual) = "port direction mismatch: " ++ (show expected) ++ "; actual port direction is " ++ (show actual)
	show (ChannelTypeMismatch expected actual) = "channel type mismatch: " ++ (show expected) ++ "; actual port channel type is " ++ (show actual)

public export
record Located l t where
	constructor MkLocated
	location : l
	payload : t

data NetworkItemLocation = Node Nat | Port Nat Nat | Edge Nat | EdgeEnd Nat PortDirection
	
NetworkProblem : Type
NetworkProblem = Located NetworkItemLocation NetworkProblemDetail

Show NetworkItemLocation where
	show (Node nodeIndex) = "node " ++ (show nodeIndex)
	show (Port nodeIndex portIndex) = "node " ++ (show nodeIndex) ++ ", port " ++ (show portIndex)
	show (Edge edgeIndex) = "edge " ++ (show edgeIndex)
	show (EdgeEnd edgeIndex Out) = "edge " ++ (show edgeIndex) ++ ", source end"
	show (EdgeEnd edgeIndex In)  = "edge " ++ (show edgeIndex) ++ ", destination end"

Show NetworkProblem where
	show prob = (show prob.payload) ++ " @ " ++ (show prob.location)

itemAt : (index : Nat) -> List x -> Maybe x
itemAt index [] = Nothing
itemAt 0 (a :: rest) = Just a
itemAt (S indexMinusOne) (a :: rest) = itemAt indexMinusOne rest

invertPortPortDirection : ProcessPort -> ProcessPort
invertPortPortDirection (MkProcessPort direction channelType) = MkProcessPort (invertPortDirection direction) channelType

invertInterfacePortDirections : (iface : ProcessInterface) -> ProcessInterface
invertInterfacePortDirections (MkProcessInterface ports) =
	MkProcessInterface (map invertPortPortDirection ports)

getNetworkNodeInterface : {iface : ProcessInterface} -> Network iface -> NetworkPortNodeRef -> Either NetworkProblemDetail ProcessInterface
getNetworkNodeInterface net (NodeIndex k) =
	case itemAt k net.nodes of
		Nothing => Left (BadNodeIndex k)
		Just node => Right node.iface
getNetworkNodeInterface net NetworkBoundary = Right (invertInterfacePortDirections iface)

getNetworkProcessPort : {iface : ProcessInterface} -> Network iface -> NetworkPortRef direction channelType -> Either NetworkProblemDetail ProcessPort
getNetworkProcessPort net (MkNetworkPortRef nodeRef portIndex) =
	case getNetworkNodeInterface net nodeRef of
		Left err => Left err
		Right nodeIface =>
			case itemAt portIndex nodeIface.ports of
				Nothing => Left (BadPortIndex portIndex)
				Just port => Right port

getEdgePortRef : {channelType : ChannelType} -> PortDirection -> NetworkEdge channelType -> SomeNetworkPortRef
getEdgePortRef Out edge = MkSomeNetworkPortRef Out channelType edge.from
getEdgePortRef In  edge = MkSomeNetworkPortRef In  channelType edge.to

validateEdgePort : {iface : ProcessInterface} -> Network iface -> PortDirection -> SomeNetworkEdge -> List NetworkProblemDetail
validateEdgePort net portDirection someEdge =
	let portRef = getEdgePortRef portDirection someEdge.edge in
		case getNetworkProcessPort net portRef.portRef of
			Left problemDetail => [problemDetail]
			Right port =>
				(if port.channelType == portRef.channelType then [] else [ChannelTypeMismatch portRef.channelType port.channelType]) ++
				(if port.direction   == portDirection       then [] else [DirectionMismatch   portDirection       port.direction  ])

validateNetworkEdge : {iface : ProcessInterface} -> Network iface -> SomeNetworkEdge -> List NetworkProblemDetail
validateNetworkEdge net someEdge =
	(validateEdgePort net Out someEdge) ++ (validateEdgePort net In someEdge)
	-- Any other validations needed here?

mapWithIndex : (Nat -> i -> o) -> Nat -> List i -> List o
mapWithIndex func i [] = []
mapWithIndex func i (a :: rest) = (func i a) :: mapWithIndex func (S i) rest

validateNetwork : {iface : ProcessInterface} -> Network iface -> List NetworkProblem
validateNetwork net =
	foldl (++) [] (mapWithIndex
		(\idx => \edge => (map (MkLocated (Edge idx)) (validateNetworkEdge net edge)))
		0 net.edges)
	-- TODO: Validate nodes, including that ProcPrograms only reference
	-- ports that are part of their interface.

---- Demonstration

-- TODO: Fix to properly UTF-8 encode!
stringToBytes : String -> List Bits8
stringToBytes s = map (cast . ord) (unpack s)

EchoIface : ProcessInterface
EchoIface = MkProcessInterface [bytesOut]

echoHelloNode : NetworkNode
-- echoHelloNode = MkNetworkNode
-- 	(MkProcessInterface [bytesIn, bytesOut, bytesOut, sysReqOut, sysResIn])
-- 	(OSCommand ["echo", "hello world"])
echoHelloNode = MkNetworkNode
	(MkProcessInterface [bytesOut, exitOut])
	(Program (do
		(WriteBytes (MkProcessPortRef 0) (stringToBytes "Hello, world!\n"))
		pure (Exited 0)))

echoToBoundary : SomeNetworkEdge
echoToBoundary = MkSomeNetworkEdge bytes (MkEdge
	(MkNetworkPortRef (NodeIndex 0) 0)
	(MkNetworkPortRef NetworkBoundary 0))

echoHelloNetwork : Network EchoIface
echoHelloNetwork = MkNetwork [echoHelloNode] [echoToBoundary]

main : IO ()
main =
	case validateNetwork echoHelloNetwork of
		[] => putStrLn "Echo network is valid"
		problems => do
			putStrLn ("Echo network is invalid. Problems:")
			printItems problems
			where
				printItems : Show t => List t -> IO ()
				printItems [] = pure ()
				printItems (i :: rest) = do
					putStrLn ("- " ++ (show i))
					printItems rest
