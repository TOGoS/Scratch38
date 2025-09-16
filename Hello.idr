module Hello

mane : String -> String
mane x = case x of
           "uhm" => "World!"
           -- Mind the indent!  If this were further left the compiler would be confused.
           otherwise => "...whoever?"

somethingWithInts : Integer -> Integer -> Integer
somethingWithInts x y = x + y

main : IO ()
main = putStrLn ("Hello, " ++ mane "uhm")
