## 2025-09-15

### Hello, Idris!

I got Idris working in a 'devcontainer' in VS code.

![Screenshot of docs (left) and Idris in a 'devcontainer' (right)](http://picture-files.nuke24.net/uri-res/raw/urn:bitprint:XYJOVKOEAQ73EQC4S5JEEYDQNEEHLFJZ.PSJWNUJKBL5O2KQDVCGEVFNW3K6ID5IV4AC46UA/20250915T21-HelloIdris.png)

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
