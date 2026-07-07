module S38S21.Scratch.ProcNet1

%default total

public export
data ProcID = OSPID Int | InternalPID Int

public export
data Exit = Exited Int | Signaled Int -- Attempt at mirroring Unix model; might need work

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

bytes : ChannelType
bytes = ByteChunk

data PortDirection = In | Out -- Into a node, and out of a node, repspectively.
Eq PortDirection where
	In  == In  = True
	Out == Out = True
	a   == b   = False

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
		iface : ProcessInterface -- TODO: Maybe not here; `SomeNetwork` to hold this at runtime if needed
		0 ifaceMatches : iface = tiface
		nodes : List NetworkNode
		edges : List SomeNetworkEdge

---- Validation

data NetworkProblemDetail
	= BadNodeIndex Nat
	| BadPortIndex Nat
	| DirectionMismatch PortDirection PortDirection -- expected, actual
	| ChannelTypeMismatch ChannelType ChannelType   -- expected, actual

-- TODO: Define a type that encapsulates a problem and a source location

itemAt : (index : Nat) -> List x -> Maybe x
itemAt index [] = Nothing
itemAt 0 (a :: rest) = Just a
itemAt (S indexMinusOne) (a :: rest) = itemAt indexMinusOne rest

invertPortPortDirection : ProcessPort -> ProcessPort
invertPortPortDirection (MkProcessPort direction channelType) = MkProcessPort (invertPortDirection direction) channelType

invertInterfacePortDirections : (iface : ProcessInterface) -> ProcessInterface
invertInterfacePortDirections (MkProcessInterface ports) =
	MkProcessInterface (map invertPortPortDirection ports)

getNetworkNodeInterface : Network iface -> NetworkPortNodeRef -> Either NetworkProblemDetail ProcessInterface
getNetworkNodeInterface net (NodeIndex k) =
	case itemAt k net.nodes of
		Nothing => Left (BadNodeIndex k)
		Just node => Right node.iface
getNetworkNodeInterface net NetworkBoundary = Right (invertInterfacePortDirections net.iface)

getNetworkProcessPort : Network iface -> NetworkPortRef direction channelType -> Either NetworkProblemDetail ProcessPort
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

validateEdgePort : Network iface -> PortDirection -> SomeNetworkEdge -> List NetworkProblemDetail
validateEdgePort net portDirection someEdge =
	let portRef = getEdgePortRef portDirection someEdge.edge in
		case getNetworkProcessPort net portRef.portRef of
			Left problemDetail => [problemDetail]
			Right port =>
				(if port.channelType == portRef.channelType then [] else [ChannelTypeMismatch portRef.channelType port.channelType]) ++
				(if port.direction   == portDirection       then [] else [DirectionMismatch   portDirection       port.direction  ])

validateNetworkEdge : Network iface -> SomeNetworkEdge -> List NetworkProblemDetail
validateNetworkEdge net someEdge =
	(validateEdgePort net Out someEdge) ++ (validateEdgePort net In someEdge)
	-- Any other validations needed here?

validateNetwork : Network iface -> List NetworkProblemDetail
validateNetwork net =
	foldl (++) [] (map (validateNetworkEdge net) net.edges)

---- Demonstration

EchoIface : ProcessInterface
EchoIface = MkProcessInterface [bytesOut]

echoHelloNode : NetworkNode
echoHelloNode = MkNetworkNode
	(MkProcessInterface [bytesIn, bytesOut, bytesOut, sysReqOut, sysResIn])
	(OSCommand ["echo", "hello world"])

echoToBoundary : SomeNetworkEdge
echoToBoundary = MkSomeNetworkEdge bytes (MkEdge
	(MkNetworkPortRef (NodeIndex 0) 1)
	(MkNetworkPortRef NetworkBoundary 0))

echoHelloNetwork : Network EchoIface
echoHelloNetwork = MkNetwork EchoIface Refl [echoHelloNode] [echoToBoundary]

main : IO ()
main =
	case validateNetwork echoHelloNetwork of
		[] => putStrLn "Echo network is valid"
		problems => putStrLn ("Echo network is invalid. Problems: " ++ show (length problems))
