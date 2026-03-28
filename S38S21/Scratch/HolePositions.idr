-- Let's do some super simple arithmetic on wall hole positions!

import Data.Vect

-- I'll make it rational + units later maybe
Position : Type
Position = Double

record WallHole where
	constructor MkWallHole
	stud : Bool
	pos : Position

record WallSection where
	constructor MkWallSection
	leftPos : Double
	holePositions : List WallHole
	rightPos : Double

transformPositions : (Position -> Position) -> WallSection -> WallSection
transformPositions xf sect =
	MkWallSection
		(xf (.leftPos sect))
		(map (\hole => MkWallHole (.stud hole) (xf (.pos hole))) (.holePositions sect))
		(xf (.rightPos sect))

putLeftAt : Position -> WallSection -> WallSection
putLeftAt pos sect = transformPositions (\pos => (.leftPos sect) - pos) sect

fromLeft : WallSection -> WallSection
fromLeft sect = putLeftAt (.leftPos sect) sect

officeEastWall : WallSection
officeEastWall = MkWallSection
	(-116 - 5/8)
	[
		MkWallHole True (0-85),
		MkWallHole True (0-69),
		MkWallHole True (0-53.5),
		MkWallHole True (0-37.5),
		MkWallHole True (0-21.5),
		MkWallHole True (0-5.5)
	]
	0
