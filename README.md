# TOGoS Learns Perl 6 a.k.a. Raku

See [helloworld.raku](./helloworld.raku).

Run the script to demonstrate some stuff.

Run `raku --doc helloworld.raku` for more notes.

## Installing Raku

Most recently I have managed to use [Rakubrew](https://rakubrew.org/)
(as recommended by [the docs on raku.org](https://raku.org/nav/1/install)),
but it is kind of complicated.

To set it up
- Create a Rakudobrew home directory
- Create a `bin` directory inside it
- Download [rakubrew.exe](https://rakubrew.org/win/rakubrew.exe) into that bin directory
- Set `RAKUBREW_HOME` to that parent directory, and add `bin` and `shims` direcotires to `$PATH`,
  or create a script to do so.

  e.g. this nushell script:
  
  ```nu
  $env.RAKUBREW_HOME = ($env.CURRENT_FILE | path dirname)
  $env.PATH = [$"($env.RAKUBREW_HOME)/bin" $"($env.RAKUBREW_HOME)/shims" ...$env.PATH] | uniq
  ```

- Run `rakubrew mode shim` to have Rakubrew generate some `.bat` scripts into that `shims` directory
- Run `rakubrew download` to download the latest version of Raku and MoarVM and stuff
- Run `rakubrew switch moar-2026.02` (or whatever was installed)
- Run `rakubrew rehash` to regenerate the shims directory

After all that, you should be able to run `raku --doc helloworld.raku`.
