## 2026-01-12

### 2026-01-12-Brainstorming

What API should my 'applets' implement?  What *is* a 'graphics demo'?

Thinking that the primary data structure represents a purely functional 'scene'
which can be entire applets in their own right.
e.g. a scene could respond to user input by returning a new scene.

For more control, could go out a level and create a program
which is itself imperative, but emits scenes as output,
similar to Scratch38-S0015 TUIAppFramework3 apps.
But that should be unnecessary for demos that only do basic,
controlled functional input/output.

Types of content
- Continuous animation
- Interactive thing
- Illustration of a multi-step process
  - A graphic at each step
  - Multiple 'layers'
  - Commentary on each step
  - Hierarchical steps, where coarse-grained ones can be broken down into smaller ones

Output formats
- Interactive 'applet'
  - JVM, JavaScript being primary targets
  - 'Player' app controls size of output, what user inputs are provided, passage of 'time'
- Static document (e.g. PDF, static HTML, slideshow)
  - A series of figures from marked steps of the scene
- Terminal???
  - With enough characters, can render vector graphics!

Common themes
- Scenes are a series (or a tree, if user input can be taken into account) of figures, possibly with additional metadata
- Figure is a static object that can be rendered to different outputs, using different viewports
  - Usually a vector graphic
  - Has built-in recommended bounds, but can be asked to draw a region overlapping with that region or not
- Lazy generation of output
  - Even if generating a static document, don't need to generate the whole thing at once
  - Document could be infinite!

### Next steps

Build out a simple test figure (not a whole dynamic steppable scene thing; just one abstract graphic!)
in `WindowedAppPlayer.java` and get something on the screen using AWT drawing primitives
(possibly abstracted by the `DestCanvas2D` interface).
