module S38S21.Scratch.TextRaster

import Data.Vect

data TextStyle = Default | Silver -- Flesh out later

data StyledChar = MkStyledChar Char TextStyle

Vect2D : (width : Nat) -> (height : Nat) -> (cell : Type) -> Type
Vect2D w h p = Vect h (Vect w p)

data TextRaster2D : (w : Nat) -> (h : Nat) -> Type where
	MkTextRaster2D : (Vect2D w h Char) -> (Vect2D w h TextStyle) -> TextRaster2D w h

renderOnto : {w : Nat} -> {h : Nat} -> TextRaster2D w h -> (minY : Integer) -> (maxY : Integer) -> (minX : Integer) -> (maxX : Integer) -> ((x : Nat) -> (y : Nat) -> StyledChar) -> TextRaster2D w h
renderOnto {w} {h} baseRaster minY maxY minX maxX update =
	if maxY <= 0 then baseRaster
	else if maxY >= (cast h) then baseRaster
	else ?updateItHere
