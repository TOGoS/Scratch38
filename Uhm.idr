module Uhm

jack : String -> String
jack x = ?whatjack

zhe : (ty : Type) -> ty -> ty
zhe ty x = x

-- typeof : (x : ty) -> ty
-- typeof x = ty -- ty is not accessible in this context
