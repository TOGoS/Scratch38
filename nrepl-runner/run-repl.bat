@echo off

java -cp "java-libs/clojure-1.11.1.jar;java-libs/spec.alpha-0.3.218.jar;java-libs/core.specs.alpha-0.2.62.jar;java-libs/nrepl-0.5.3.jar;java-libs/bencode-1.0.0.jar" clojure.main -m nrepl.cmdline -c --host 127.0.0.1 --port 9876
