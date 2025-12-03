let clojure_cp = 'C:\Users\TOGoS\.m2\repository\org\clojure\clojure\1.12.3\clojure-1.12.3.jar;C:\Users\TOGoS\.m2\repository\org\clojure\spec.alpha\0.5.238\spec.alpha-0.5.238.jar;C:\Users\TOGoS\.m2\repository\org\clojure\core.specs.alpha\0.4.74\core.specs.alpha-0.4.74.jar'

def --wrapped clojure [...rest] {
	java -cp $clojure_cp clojure.main ...$rest
}
