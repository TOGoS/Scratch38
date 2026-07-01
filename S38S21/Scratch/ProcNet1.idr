module S38S21.Scratch.ProcNet1

%default total

public export
data ProcID = OSPID Int | InternalPID Int

public export
data Exit = Exited Int | Signaled Int -- Attempt at mirroring Unix model; might need work

data ChannelType = Chunks Type | ExitEvent | UnitValue | SysReq | SysRes

bytes : ChannelType
bytes = Chunks Bits8

data PortDirection = In | Out -- Into a node, and out of a node, repspectively.

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

data NetworkPortNode = NodeIndex Nat | NetworkBoundary

public export
record NetworkPort (direction : PortDirection) (channelType : ChannelType) where
	constructor MkNetworkPort
	-- Note that a network input is represented as a port with node = NetworkBoundary and direction = Out.
	-- i.e. the network's inputs appear as outputs, and outputs appear as inputs, from the perspective of its internal edges.
	node      : NetworkPortNode
	portIndex : Nat

public export
record NetworkEdge (channelType : ChannelType) where
	constructor MkEdge
	from : NetworkPort Out channelType
	to   : NetworkPort In channelType

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
	
	public export
	record Network (iface : ProcessInterface) where
		constructor MkNetwork
		nodes : List NetworkNode
		edges : List SomeNetworkEdge
