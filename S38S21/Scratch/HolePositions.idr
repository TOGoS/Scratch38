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

record WallFeature pos where
	constructor MkWallFeature
	featureType : WallFeatureType
	position : pos

wallFeatureTypeStr : WallFeatureType -> String
wallFeatureTypeStr LeftEdge = "[";
wallFeatureTypeStr Stud = "S";
wallFeatureTypeStr Point = ".";
wallFeatureTypeStr RightEdge = "]";

wallFeatureStr : WallFeature _ -> String
wallFeatureStr = wallFeatureTypeStr . featureType

record WallSection pos where
	constructor MkWallSection
	features : List (WallFeature pos)
	-- Want to declare that things are in order?
	-- Just define an 'auto' compile-time-only property of type `So (expression that must be true)`:
	-- {auto 0 inOrder : So (isInOrder (map (WallFeature.position) features))}
	-- There may be a better way to declare this
	-- {auto 0 isNonEmpty : So (isNonEmpty features)}
	-- Commented-out because I'd have to prove that transformPositions
	-- obeys the constraint, which it doesn't if xf can be anything!

transformPositions : (posA -> posB) -> WallSection posA -> WallSection posB
transformPositions xf sect =
	MkWallSection
		(map (\feat => MkWallFeature (.featureType feat) (xf (.position feat))) (.features sect))

leftEdge : pos -> WallSection pos -> pos
leftEdge defalt (MkWallSection []) = defalt
leftEdge defalt (MkWallSection (f :: fs)) = .position f

putLeftAt : (Num pos, Neg pos) => pos -> WallSection pos -> WallSection pos
putLeftAt newLeftPosition sect =
	let shift = (leftEdge 0 sect) - newLeftPosition
	in transformPositions (+ shift) sect

fromLeft : (Num pos, Neg pos) => WallSection pos -> WallSection pos
fromLeft sect = putLeftAt (leftEdge 0 sect) sect

record WallSpan pos where
	constructor MkWallSpan
	leftPosition : pos
	rightPosition : pos
	-- {auto 0 ... : So ...}; without the braces and 'auto',
	-- you'd have to explicitly pass `Oh` as a parameter.
	-- {auto 0 rightGteLeft : So (leftPosition <= rightPosition)}

shiftWallSpan : Num d => d -> WallSpan d -> WallSpan d
shiftWallSpan n (MkWallSpan left right {- {ordPos} {rightGteLeft} -} ) =
	MkWallSpan (left + n) (right + n)
		{-
		{ordPos}
		{rightGteLeft = believe_me rightGteLeft}
		-}

record WallBoardProtoChart pos where
	constructor MkWallBoardProtoChart
	wallSection : WallSection pos
	boardSpans : List (WallSpan pos)

wallFeaturesToAscii : (scale : pos -> Integer) -> List (WallFeature pos) -> (col0 : Nat) -> (length : Nat) -> String
wallFeaturesToAscii scale features col0 0 = ""
wallFeaturesToAscii scale [] col0 (S lengthMinus1) = " " ++ (wallFeaturesToAscii scale [] (S col0) lengthMinus1)
wallFeaturesToAscii scale (feat :: rest) col0 (S lengthMinus1) =
	let nextFeatCol = (scale (.position feat)) in
	if nextFeatCol < (natToInteger col0) then
		-- Skip it!
		wallFeaturesToAscii scale rest col0 (S lengthMinus1)
	else if nextFeatCol > (natToInteger col0) then
		-- Space until we get there
		" " ++ (wallFeaturesToAscii scale (feat :: rest) (S col0) lengthMinus1)
	else
		(wallFeatureStr feat) ++ (wallFeaturesToAscii scale rest (S col0) lengthMinus1)


positionsToLabels : (scale : pos -> Integer) -> (posToText : pos -> String) -> List pos -> List (Pair Integer String)
positionsToLabels scale posToText =
	map (\pos => MkPair (scale pos) (posToText pos))

layOutLabel : Pair Integer String -> List (List (Pair Integer String)) -> List (List (Pair Integer String))
layOutLabel label [] = [[label]]
layOutLabel label ([] :: moreRows) = [label] :: moreRows -- This case probably won't happen!
layOutLabel label ((nextLab :: restOfTopRow) :: moreRows) =
	let MkPair labPos labText = label in
	let labEnd = labPos + cast (length labText) in
	let (MkPair nextPos nextText) = nextLab in
	if labEnd >= nextPos then
		(nextLab :: restOfTopRow) :: layOutLabel label moreRows
	else
		(label :: nextLab :: restOfTopRow) :: moreRows

layOutLabels : List (Pair Integer String) -> List (List (Pair Integer String))
layOutLabels [] = []
layOutLabels (label :: moreLabels) =
	layOutLabel label (layOutLabels moreLabels)

labelsToAscii : List (Pair Integer String) -> (col0 : Nat) -> (length : Nat) -> String
labelsToAscii labels col0 0 = ""
labelsToAscii [] col0 (S lengthMinus1) = " " ++ labelsToAscii [] (S col0) lengthMinus1
labelsToAscii (label :: moreLabels) col0 (S lengthMinus1) =
	let MkPair labPos labText = label in
	if labPos == cast col0 then
		?uhhhhh
	else
		" " ++ labelsToAscii (label :: moreLabels) (S col0) lengthMinus1

wallBoardChartToAscii : (Num pos, Neg pos) => (scale : pos -> Integer) -> (width : Nat) -> WallBoardProtoChart pos -> String
wallBoardChartToAscii scale width chart =
	wallFeaturesToAscii scale (.features (.wallSection chart)) 0 width

officeEastWall : WallSection Double
officeEastWall = MkWallSection
	[
		MkWallFeature LeftEdge (-116 - 5/8),
		MkWallFeature Stud (0-85-16), -- Presumably!
		MkWallFeature Stud (0-85),
		MkWallFeature Stud (0-69),
		MkWallFeature Stud (0-53.5),
		MkWallFeature Stud (0-37.5),
		MkWallFeature Stud (0-21.5),
		MkWallFeature Stud (0-5.5),
		MkWallFeature RightEdge (0)
	]

aFrenchCleatSpan : WallSpan Double
aFrenchCleatSpan = MkWallSpan
	(-116 + 1/8)
	(-116 + 1/8 + 96) -- Let's say

aChart : WallBoardProtoChart Double
aChart = MkWallBoardProtoChart officeEastWall [aFrenchCleatSpan]

x : Nat
x = integerToNat (0-4)

i : Integer
i = cast (floor 42.0)

Show (WallBoardProtoChart Double) where
	show chart =
		let leftEdge = leftEdge 0 (wallSection chart)
		in wallBoardChartToAscii (cast . floor . (\x => x - leftEdge)) 120 chart

main : IO ()
main = putStrLn (show aChart)
