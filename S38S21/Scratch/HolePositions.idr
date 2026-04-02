module S38S21.Scratch.HolePositions

-- Let's do some super simple arithmetic on wall hole positions!

import Data.Vect

-- I'll make it rational + units later maybe
Position : Type
Position = Double

isInOrder : Ord x => List x -> Bool
isInOrder Nil = True
isInOrder (a :: Nil) = True
isInOrder (a :: b :: rest) =
	a <= b && isInOrder (b :: rest)

isNonEmpty : List n -> Bool
isNonEmpty [] = False
isNonEmpty _ = True

data WallFeatureType = LeftEdge | Stud | Point | RightEdge

record WallFeature where
	constructor MkWallFeature
	featureType : WallFeatureType
	position : Position

record WallSection where
	constructor MkWallSection
	features : List WallFeature
	-- Want to declare that things are in order?
	-- Just define an 'auto' compile-time-only property of type `So (expression that must be true)`:
	-- {auto 0 inOrder : So (isInOrder (map (WallFeature.position) features))}
	-- There may be a better way to declare this
	-- {auto 0 isNonEmpty : So (isNonEmpty features)}
	-- Commented-out because I'd have to prove that transformPositions
	-- obeys the constraint, which it doesn't if xf can be anything!

transformPositions : (Position -> Position) -> WallSection -> WallSection
transformPositions xf sect =
	MkWallSection
		(map (\feat => MkWallFeature (.featureType feat) (xf (.position feat))) (.features sect))

leftEdge : WallSection -> Position
leftEdge (MkWallSection []) = 0
leftEdge (MkWallSection (f :: fs)) = .position f

putLeftAt : Position -> WallSection -> WallSection
putLeftAt pos sect = transformPositions (\pos => (leftEdge sect) - pos) sect

fromLeft : WallSection -> WallSection
fromLeft sect = putLeftAt (leftEdge sect) sect

record WallSpan where
	constructor MkWallSpan
	leftPosition : Position
	rightPosition : Position
	-- {auto 0 ... : So ...}; without the braces and 'auto',
	-- you'd have to explicitly pass `Oh` as a parameter.
	{auto 0 rightGteLeft : So (leftPosition <= rightPosition)}

shiftWallSpan : Double -> WallSpan -> WallSpan
shiftWallSpan n (MkWallSpan left right) =
	MkWallSpan (left + n) (right + n)
		{rightGteLeft = believe_me rightGteLeft}

record WallBoardProtoChart where
	constructor MkWallBoardProtoChart
	wallSection : WallSection
	boardSpans : List WallSpan

officeEastWall : WallSection
officeEastWall = MkWallSection
	[
		MkWallFeature LeftEdge (-116 - 5/8),
		MkWallFeature Stud (0-85),
		MkWallFeature Stud (0-69),
		MkWallFeature Stud (0-53.5),
		MkWallFeature Stud (0-37.5),
		MkWallFeature Stud (0-21.5),
		MkWallFeature Stud (0-5.5),
		MkWallFeature RightEdge (0)
	]

aFrenchCleatSpan : WallSpan
aFrenchCleatSpan = MkWallSpan
	(-116 + 1/8)
	(-116 + 1/8 + 96) -- Let's say

aChart : WallBoardProtoChart
aChart = MkWallBoardProtoChart officeEastWall [aFrenchCleatSpan]
