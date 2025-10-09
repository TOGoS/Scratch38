module S38S21.Scratch.Stream

-- This also works, but I think `(x : Type) => ` is implicit.  That's what lowercase names are for.
-- makeStream : (x : Type) => x -> Stream x
makeStream : x -> Stream x
makeStream x = x :: makeStream x

(++) : List x -> Stream x -> Stream x
(++) l s = case l of
            [] => s
            h :: rest => h :: (rest ++ s)
