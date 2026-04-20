module S38S21.TDDWI.Ch4.Pictures

import S38S21.TDDWI.Ch4.Shapes

data Picture
	= Primitive Shape
	| Union Picture Picture
	| Rotate Double Picture
	| Translate Double Double Picture

testPicture =
	Union
		(Translate 5 5 (Primitive (Rectangle 20 10)))
		(Union
			(Translate 35 5 (Primitive (Circle 5)))
			(Translate 15 25 (Primitive (Triangle 10 10))))

%name Shape shape, shape1, shape2
%name Picture pic, pic1, pic2

area : Picture -> Double
area (Primitive shape) = area shape
area (Union pic1 pic2) = area pic1 + area pic2 -- Assuming they don't overlap!
area (Rotate r pic) = area pic
area (Translate x y pic) = area pic

public export
allPrimitives : Picture -> List Shape
allPrimitives (Primitive p) = [p]
allPrimitives (Union a b) = allPrimitives a ++ allPrimitives b
allPrimitives (Rotate r pic) = allPrimitives pic
allPrimitives (Translate x y pic) = allPrimitives pic

reduce : (b -> a -> b) -> b -> List a -> b
reduce func left [] = left
reduce func left (right :: rest) = reduce func (func left right) rest

maxMaybe : Ord n => Maybe n -> Maybe n -> Maybe n
maxMaybe Nothing Nothing = Nothing
maxMaybe Nothing (Just b) = Just b
maxMaybe (Just a) Nothing = Just a
maxMaybe (Just a) (Just b) = if a > b then Just a else Just b

public export
areaOfBiggestTriangle : Picture -> Maybe Double
areaOfBiggestTriangle pic =
	reduce (\a => \b => maxMaybe a (areaOfTriangle b) ) Nothing (allPrimitives pic)
	where
		areaOfTriangle : Shape -> Maybe Double
		areaOfTriangle tri@(Triangle w h) = Just (Shapes.area tri)
		areaOfTriangle _ = Nothing
