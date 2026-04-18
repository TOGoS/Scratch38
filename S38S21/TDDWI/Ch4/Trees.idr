module S38S21.TDDWI.Ch4.Trees

public export
data Tree elem = Empty | Node (Tree elem) elem (Tree elem)

insert : Ord elem => elem -> Tree elem -> Tree elem
insert val Empty = Node Empty val Empty
insert val originalTree@(Node nLeft nVal nRight) =
	case compare val nVal of
		LT => (Node (insert val nLeft) nVal nRight)
		EQ => originalTree -- Return the original tree, says TDDWI
		GT => (Node nLeft nVal (insert val nRight))

fromList : Ord elem => List elem -> Tree elem
fromList [] = Empty
fromList (val :: rest) = insert val (fromList rest)

toList : Ord elem => Tree elem -> List elem
toList Empty = []
toList (Node left val right) = (toList left) ++ [val] ++ (toList right)
