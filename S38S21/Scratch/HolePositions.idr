module S38S21.Scratch.HolePositions

-- Let's do some super simple arithmetic on wall hole positions!

import Data.Vect

-- I'll make it rational + units later maybe
Position : Type
Position = Double

isInOrder : List Position -> Bool
isInOrder Nil = True
isInOrder (a :: Nil) = True
isInOrder (a :: b :: rest) =
	a <= b && isInOrder (b :: rest)

data WallFeatureType = LeftEdge | Stud | Point | RightEdge

record WallFeature where
	constructor MkWallFeature
	featureType : WallFeatureType
	position : Position

record WallHole where
	constructor MkWallHole
	stud : Bool
	pos : Position

record WallSection where
	constructor MkWallSection
	leftPos : Position
	holePositions : List WallHole
	rightPos : Position
	-- Want to declare that things are in order?
	-- Just define a compile-time-only property of type `So (expression that must be true)`:
	-- 0 inOrder : So (isInOrder ((leftPos :: (map (.pos) holePositions)) ++ [rightPos]))
	-- It might be better if the left and right positions
	-- were just wall features themselves.

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

record WallSpan where
	constructor MkRange
	leftPosition : Position
	rightPosition : Position
	0 rightGteLeft : So (leftPosition <= rightPosition)

record WallBoardProtoChart where
	constructor MkWallBoardProtoChart
	wallSection : WallSection
	boardSpans : List WallSpan

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
