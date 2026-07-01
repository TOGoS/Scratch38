module S38S21.Scratch.ProcNet1

%default total

public export
data ProcID = OSPID Int | InternalPID Int

public export
data Exit = Exited Int | Signaled Int -- Attempt at mirroring Unix model; might need work

data ChannelType = Bytes | TextLines | ExitEvent | UnitValue | SysReq | SysRes

public export
record ProcessInterface where
	constructor MkProcessInterface
	ins  : List ChannelType
	outs : List ChannelType

data PortDir = In | Out

data NetworkPortNode = NodeIndex Nat | NetworkBoundary

public export
record NetworkPort where
	constructor MkNetworkPort
	-- Note that a network input is represented as a port with node = NetworkBoundary and direction = Out.
	-- i.e. the network's inputs appear as outputs, and vice-versa, from the perspective of its internal edges.
	node      : NetworkPortNode
	dir       : PortDir
	portIndex : Nat

public export
record NetworkEdge where
	constructor MkEdge
	from : NetworkPort
	to   : NetworkPort

mutual
	data ProtoProcess : (iface : ProcessInterface) -> Type where
		-- TODO: OSCommand should have a whole environment, too.
		OSCommand : (argv : List String) -> ProtoProcess (MkProcessInterface [Bytes, SysRes] [Bytes, Bytes, SysReq])
		PureExit : (run : Unit -> Int) -> ProtoProcess (MkProcessInterface [] [ExitEvent])
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
		edge  : List NetworkEdge
