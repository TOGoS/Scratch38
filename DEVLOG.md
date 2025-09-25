## 2025-09-15

### Hello, Idris!

I got Idris working in a 'devcontainer' in VS code.

![Screenshot of docs (left) and Idris in a 'devcontainer' (right)](http://picture-files.nuke24.net/uri-res/raw/urn:bitprint:XYJOVKOEAQ73EQC4S5JEEYDQNEEHLFJZ.PSJWNUJKBL5O2KQDVCGEVFNW3K6ID5IV4AC46UA/20250915T21-HelloIdris.png)

This was by following the instructions for using [joshuanianji's idris-2-docker devcontainer](https://github.com/joshuanianji/idris-2-docker/pkgs/container/idris-2-docker%2Fdevcontainer#devcontainer)

I'm not sure how exactly this works, but it *seems* like...
When you tell VS code to reopen the project in a container,
it creates the docker container, then uses some WSL magic to
mount the directory inside that Docker container,
and I'm not sure if VS code is itself running inside it, like it does over SSH?
Because all the files I'm writing are visible on the host machine
and also inside the Docker container.

Of course when I open `bash` in the terminal, that's running Bash
inside the container.

I guess maybe it doesn't matter exactly how much of VS code is running
inside and out; the abstraction is that it doesn't matter.

### Compile with -o

```bash
idris -o hello123 Hello.idr
```

does *not* create an executable file 'hello123'.

But that file does appear in `build/exec'.

I can force it to write it in a certain directory by using an absolute path.

```bash
idris2 -o "$(pwd)/hello" Hello.idr
```

### What next?

What's my goal with this?

Learn a better language for prototyping my funny ideas, I suppose,
even if I end up having to rebuild them in another language.

But maybe Idris's type system is powerful enough that I can
use it to generate code in those other languages!  Hmm.
Yeah, that'd be super.

> Like Idris 1, Idris 2 [supports plug-in code generation](https://idris2.readthedocs.io/en/latest/backends/custom.html)
> to allow you to write a back end for the platform of your choice.

Oh goodie!

### Disable 'Chords' In Terminal

By default, hitting Ctrl+k in the terminal inside VS Code just pops
up a message at the bottom of the window that says:

> (Ctrl+K) was pressed.  Waiting for second key of chord...

Which, I don't know what these chords are, but that's not very useful to me,
and certainly not when it prevents me from deleting text in the terminal.

Found the solution [on stackoverflow](https://stackoverflow.com/questions/50569100/vscode-how-to-make-ctrlk-kill-till-the-end-of-line-in-the-terminal).

```
"terminal.integrated.allowChords": false
```

Or go to File → Preferences → Settings → User (tab), search for allowChords and uncheck it.

I am curious what these 'chords' are for but my searches mostly just bring
up people having problems with them.

## 2025-09-16

### Trying to get idris2-lsp working

Asked question in https://discord.com/channels/827106007712661524/1021166285343686666,
then putzed about with the `.ipkg` file.

What I have found is:
- Name of file does not need to match package name inside file
- Package name declared inside the `.ipkg` file must be 'a valid idris identifier',
  or be quoted.
- Changes to the `.ipkg` file will only take effect after saving your `.idr` file
- To test if `.ipkg` is set up correctly, make a small change to your `.idr`
  (e.g. insert and delete a space, just so VS code will let you re-save it),
  put cursor over a `?hole`, and hit (Ctrl+.).
  If all is well, it should give options to 'make lemma', 'make with', 'make case'.

Works:

```ipkg
package "Scratch38-S0021"
```

Works:

```ipkg
package Scratch38S0021
```

Does *not* work:

```ipkg
package Scratch38-S0021
```

Bad package name will not show any obvious errors,
but VS code will not be able to use Idris LSP,
which means (Ctrl+.) will not do anything for you.

For now I'm going with:
```ipkg
package "Scratch38-S0021"
```

Dinked around with [Hello.idr](./Hello.idr).
Added a `case` expression to map "uhm" to "World!"
and every other string to something else.
Hint: the exact amount of indentation seems to be important!

![Screenshot of my dumb program](http://picture-files.nuke24.net/uri-res/raw/urn:bitprint:JDTS5SVS2ROXG3X2M5VGBEDJFGD36SVR.H73RWFTMXRMJONVB6MLSNLWOYTWM7E2BKWL433Q/20250916T12-HelloIdris2.png)

Okay, seems I'm off to the races, as they say!

...though maybe I spoke too soon.  Code actions are empty again.
Hovering is not showing types (was it ever?).
Some combination of removing the `otherwise`
(idris2-lsp seems to want it to be `else`),
`:r`eloading in the REPL, re-saving `Hello.idr`...seems to sometimes help.

Finding:

```idris2
jim : String
jim = ?whoIsJim
-- Ctrl+. works on ?whoIsJim

mane : String -> String
mane x = case x of
           "uhm" => "World!"
           -- Mind the indent!  If this were further left the compiler would be confused.
           otherwise => "...whoever?"

jack : String
jack = ?whoIsJack
-- Ctrl+. does NOT work on ?whoIsJack, EVEN IF mane x is entirely commented-out or removed!
```

### Continued

Added `Uhm.idr`, saved a few things...

Ooh, I got some output from 'Idris 2 LSP Client':

```
[Error - 6:06:25 PM] Request textDocument/codeAction failed.
  Message: Cannot load ipkg file for file:///workspaces/Scratch38-S0021/Hello.idr: "Error: \ESC[1mExpected package name.\ESC[0m\n\n\ESC[38;5;12m\"Scratch38-S0021.ipkg\":1:9--1:26\ESC[0m\n \ESC[38;5;12m1 |\ESC[0m package \"Scratch38-S0021\"\n             \ESC[38;5;9;1m^^^^^^^^^^^^^^^^^\ESC[0m\n"
  Code: 3 
[Error - 6:06:26 PM] Request textDocument/codeAction failed.
  Message: Cannot load ipkg file for file:///workspaces/Scratch38-S0021/Uhm.idr: "Error: \ESC[1mExpected package name.\ESC[0m\n\n\ESC[38;5;12m\"Scratch38-S0021.ipkg\":1:9--1:26\ESC[0m\n \ESC[38;5;12m1 |\ESC[0m package \"Scratch38-S0021\"\n             \ESC[38;5;9;1m^^^^^^^^^^^^^^^^^\ESC[0m\n"
  Code: 3 
```

As if it finally decided my package name sucks.

If I change it, will code actions and stuff suddenly become more reliable?

Changed the first line of [Scratch38-S0021.ipkg](./Scratch38-S0021.ipkg) to:

```ipkg
package scratch38s21
```

...and yes, the IDE stuff seems to be working a lot better, now.
It even shows more info about types when I hover over stuff,
which I don't think it was doing at all before.

![Screenshot showing some info about ?whatjack](http://picture-files.nuke24.net/uri-res/raw/urn:bitprint:AT7MPUIPFLPTK3IKKNYW4UYB7YKKSHHR.VBCSPDYO3ZD2PAEJRPRLJUJELYU4DLXQIXIJ3AI/20250916T13-WhatJack.png)

## 2025-09-18

### Streams

A `Stream` is like a list, but infinite.  `head` and `tail` work on it the same way.

You can't cast a `List` to a `Stream`, presumably because the `Stream` needs to keep going forever,
but `cast` wouldn't know what to do past the end of the list.

So to test out my understanding, I wrote `makeStream` and `(++)` functions in [Stream.idr](./Stream.idr).
I was hoping the compiler could suggest implementations for me,
but the best it did for `(++) : (l : List x) -> (s : Stream x) -> Stream x` was `s`:

```
Main> :ps 6 uhm
s
```

Maybe I don't know how to use it properly yet.

Anyway, kind of cool that I managed to write `makeStream` and `++`,
and presumably, since they compiled, they will always work, because Idris.

### Vect

Vect doesn't seem to be part of the prelude.

How to find packages?

I resorted to

```sh
find /usr/local/lib/idris2//idris2-0.7.0/ -name 'Vect*'
```

Which said...

```
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/Data/Vect
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/Data/Vect.idr
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/2023090800/Data/Vect.ttm
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/2023090800/Data/Vect
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/2023090800/Data/Vect.so
/usr/local/lib/idris2//idris2-0.7.0/base-0.7.0/2023090800/Data/Vect.ttc
/usr/local/lib/idris2//idris2-0.7.0/contrib-0.7.0/Data/Vect
/usr/local/lib/idris2//idris2-0.7.0/contrib-0.7.0/2023090800/Data/Vect
```

Not sure what these different locations are about, but I guess it's in `Data`:

```
Main> :import Data.Vect
Imported module Data.Vect
```

Yay.

```
Main> :t (3 :: (the (Vect 2 Nat) [1,2]))
3 :: the (Vect 2 Nat) [1, 2] : Vect 3 Nat
```

W00t.

If you don't care to specify the length and type,
you still need to put placeholders:

```
Main> :t (the (Vect _ _) [1,2,3])
the (Vect 3 Integer) [1, 2, 3] : Vect 3 Integer
```

Using holes also works:

```
Main> the (Vect ?l ?t) [1,2,3]
[1, 2, 3]
```

But unbound names does not work in this context:

```
Main> the (Vect l t) [1,2,3]
Error: Undefined name l.

(Interactive):1:11--1:12
 1 | the (Vect l t) [1,2,3]
```

### SQLite?

https://github.com/stefan-hoeck/idris2-sqlite3

Maybe the stuff that I was going to do in Haxe (because Deno doesn't talk SQLite)
could be done in Idris!

I'd have to figure out how to install this package, of course.

## 2025-09-10

### Done: Exit with non-zero exit code without throwing an exception

```idris2
module Exit

import System

exitCode : Int
exitCode = 3

main : IO ()
main = do
	putStrLn ("Exiting with status " ++ (cast exitCode))
	exitWith (ExitFailure exitCode)
```

Then

```bash
idris2 -o Exit Exit.idr
build/exec/Exit
echo "Exited with status $?"
```

Results in:

```
Exiting with status 3
Exited with status 3
```

w00t.  Why can't [[https://github.com/unisonweb/unison/issues/5398][Unison do that]], huh?

## 2025-09-17

Worked through the exercises at the end of [TDDWI](https://www.manning.com/books/type-driven-development-with-idris) Chapter 2.  Results in [Ch2.idr](./Ch2.idr).

## 2025-09-18

While reading chapter 3 I got some ideas,
one of which was to try to define a type that wraps
strings and knows their length, like a `Vect`.

Results of that in [LString.idr](./LString.idr).

I got the basic constructor working, but haven't
figured out how to make `++` work.
Maybe I need to read further into the book.

Work on actual content of chapter 3 is in [Ch3.idr](./Ch3.idr).
