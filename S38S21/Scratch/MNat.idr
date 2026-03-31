-- Let's implement our own Nat-like type

module S38S21.Scratch.MNat

data MNat = Z | S MNat

isEven : MNat -> Bool
isEven Z = True
isEven (S mnat) = not (isEven mnat)

Eq MNat where
	Z == Z         = True
	Z == S y       = False
	S x == Z       = False
	(S x) == (S y) = x == y
-- (/=) is defined by default as (not (==))

fromNat : Nat -> MNat
fromNat Z = Z
fromNat (S k) = S (fromNat k)

Num MNat where
	fromInteger n = fromNat (integerToNat n)
	((+) Z m) = m
	((+) n Z) = Z
	((+) n (S m)) = S (n + m)
	((*) Z m) = Z
	((*) n Z) = Z
	((*) n (S m)) = n + (n * m)

interface ToNat a where
	toNat : a -> Nat

ToNat MNat where	
	toNat Z = Z
	toNat (S n) = S (toNat n)

ToNat Nat where
	toNat n = n

Show MNat where
	show mnat = "(the MNat " ++ (show (toNat mnat)) ++ ")"

-- You can define a == that takes any two things whose types are ToNat.
-- You probably *shouldn't* do this, because
-- (a) it's generally better not to consider values of different types as equal, and
-- (b) `(a == b)` is now ambiguous!  Hence the need to say `Prelude.` in the definition.
-- (==) : ToNat a => ToNat b => a -> b -> Bool
-- (==) x y = Prelude.(==) (toNat x) (toNat y)
