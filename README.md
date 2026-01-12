# Scratch38-S0025 - Clojure Graphics Demos

Sort of 'Vizations, 2026 Edition'.

### Goal: 'Applets' of a sort, defined in Clojure code

...that can run in JVM or HTML/JavaScript/Canvas 'players'.
This will require defining an API for content that can be played.

Prior art:
- Java applets
- Flash
- SynthGen2100-P0002 devices
- Scratch38-S0015 TUIAppFramework3 apps

The common thread being that these all define programs
that can be run on some virtual machine with constrained inputs/outputs
rather than direct access to the host operating system.

The specific capabilities and APIs are not important.
What is important, for purposes of this project, is that the API is simple
and abstract enough to be easily ported to different platforms.

### Extended Goal: Transcend Clojure

This would probably mean defining a small scheme-like language that piggy-backs on
Clojure's syntax (but avoids Clojurisms like vectors) that can do everything
the Clojure applets can, but can also be compiled to other targets.

## C0100: Java GUI framework

Goal is to define a simple, platform-agnostic scene rendering API.

To run the demo:

```
mvn -f c0100-pom.xml clean test package
javaw -cp target\scratch38-s0025-c0100-0.0.1-SNAPSHOT.jar net.nuke24.scratch30.s0025.c0100.WindowedAppPlayer
```
