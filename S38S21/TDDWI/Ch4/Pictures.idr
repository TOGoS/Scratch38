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
