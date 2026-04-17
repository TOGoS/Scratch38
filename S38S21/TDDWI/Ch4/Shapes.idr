module S38S21.TDDWI.Ch4.Shapes

{--
||| Represents some stupid shapes lol
data Shape
	= ||| A dumbass triangle, defined by its base and height
	  Triangle Double Double
	| ||| A crappy rectangle, defined by its stupid width and height
	  Rectangle Double Double
	| ||| A sucky circle, defined by its craptacular radius
	  Circle Double
--}

-- The 'more verbose, but more general and flexible' syntax:
||| Represents some stupid shapes lol
data Shape : Type where
	||| A dumbass triangle, defined by its base and height
	Triangle : Double -> Double -> Shape
	||| A crappy rectangle, defined by its stupid width and height
	Rectangle : Double -> Double -> Shape
	||| A sucky circle, defined by its craptacular radius
	Circle : Double -> Shape

area : Shape -> Double
area (Triangle base height) = base * height
area (Rectangle length height) = length * height
area (Circle radius) = pi * radius * radius
