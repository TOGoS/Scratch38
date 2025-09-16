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
