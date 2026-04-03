# Makefile to help remind me how to run Idris programs.
# Should be run in a devcontainer or something,
# where `idris2` is available on the path.

.PHONY: run-exit-demo
run-exit-demo:
	idris2 S38S21/Scratch/Exit.idr -x main

.PHONY: generate-wall-chart
generate-wall-chart:
	idris2 S38S21/Scratch/HolePositions.idr -x main
