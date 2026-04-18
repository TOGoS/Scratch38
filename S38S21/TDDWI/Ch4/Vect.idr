module S38S21.TDDWI.Ch4.Vect

import Data.Fin

public export
data Vect : Nat -> Type -> Type where
	Nil : Vect Z a
	(::) : (x : a) -> (xs : Vect n a) -> Vect (S n) a

%name Vect xs, ys, zs

public export
append : Vect n elem -> Vect m elem -> Vect (n + m) elem
append [] ys = ys
append (x :: xs) ys = x :: append xs ys

public export
zip : Vect n a -> Vect n b -> Vect n (a, b)
zip [] [] = []
zip (a :: as) (b :: bs) = (a, b) :: zip as bs

-- `Fin n` can contain Nats up to, but not including, n.
-- Which corresponds nicely with 
public export
index : Fin n -> Vect n a -> a
index FZ (x :: xs) = x
index (FS n) (x :: xs) = index n xs

public export
Functor (Vect x) where
	map f [] = []
	map f (x :: xs) = f x :: map f xs
