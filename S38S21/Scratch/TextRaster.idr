module S38S21.Scratch.TextRaster

import Data.Vect

data TextStyle = Silver | White | Green -- Flesh out later

data StyledChar = MkStyledChar Char TextStyle

Vect2D : (width : Nat) -> (height : Nat) -> (cell : Type) -> Type
Vect2D w h p = Vect h (Vect w p)

data TextRaster2D : (w : Nat) -> (h : Nat) -> Type where
	MkTextRaster2D : (Vect2D w h StyledChar) -> TextRaster2D w h

indexedMap : (firstIndex : Nat) -> (f : (index : Nat) -> x -> y) -> Vect l x -> Vect l y
indexedMap i f [] = []
indexedMap i f (x :: xs) = f i x :: indexedMap (S i) f xs

renderOnto : {w : Nat} -> {h : Nat} -> TextRaster2D w h ->
	(minY : Integer) -> (maxY : Integer) -> (minX : Integer) -> (maxX : Integer) ->
	((x : Nat) -> (y : Nat) -> StyledChar) -> TextRaster2D w h
renderOnto {w} {h} baseRaster minY maxY minX maxX update =
	if maxY <= 0 then baseRaster
	else if maxY >= (cast h) then baseRaster
	else
		let MkTextRaster2D baseRows = baseRaster in
		MkTextRaster2D	(indexedMap 0 rewriteRow baseRows) where
			rewriteRow : Nat -> Vect w StyledChar -> Vect w StyledChar
			rewriteRow y row =
				if (cast y) < minY || (cast y) >= maxY then row
				else indexedMap 0 rewriteCell row where
					rewriteCell : Nat -> StyledChar -> StyledChar
					rewriteCell x oldSc =
						if (cast x) < minX || (cast x) >= maxX then oldSc
						else
							let MkStyledChar newChar newStyle = update x y in
							if newChar == '\0' then oldSc
							else MkStyledChar newChar newStyle

rowToString : Vect l StyledChar -> String
rowToString [] = ""
rowToString ((MkStyledChar char style) :: rest) = strCons char (rowToString rest)

Show (TextRaster2D w h) where
	show (MkTextRaster2D []) = ""
	show (MkTextRaster2D (row :: [])) = rowToString row
	show (MkTextRaster2D (row :: rest)) = rowToString row ++ "\n" ++ show (MkTextRaster2D rest)		

someOldRaster = MkTextRaster2D [
	[a, a, a, a, a],
	[a, a, b, b, b],
	[b, b, b, b, a]
] where
	a = MkStyledChar '/' Silver
	b = MkStyledChar '#' Green

main : IO ()
main = do
	putStrLn "Some old raster:"
	putStrLn (show (
		renderOnto someOldRaster 1 2 1 4
			(\x => (\y => if x == y then a else b))))
		where
			a = MkStyledChar 'A' Silver
			b = MkStyledChar 'B' Green
