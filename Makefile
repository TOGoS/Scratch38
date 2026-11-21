SHELL := bash
export TOGETL_ROOT := .

.DELETE_ON_ERROR:

.PHONY: all
all: out/.tests-ran
	@echo '`make run-tests` to manually run tests again'

.PHONY: clean
clean:
	rm -rf out

LIBS := lib/sqlite-jdbc-3.46.1.3.jar

out/.main-classes-built: ${LIBS} $(shell find src/main/java -name '*.java')
	mkdir -p out/classes
	find src/main/java -name '*.java' | sort > out/main-sources.txt
	. scripts/java-env.sh && \
	"$$JAVAC" $$JAVAC_FLAGS -encoding UTF-8 -Xlint:all -cp "lib/*" \
	    -d out/classes @out/main-sources.txt
	touch "$@"

out/.test-classes-built: ${LIBS} $(shell find src/test/java -name '*.java') out/.main-classes-built
	mkdir -p out/test-classes
	find src/test/java -name '*.java' | sort > out/test-sources.txt
	. scripts/java-env.sh && \
	"$$JAVAC" $$JAVAC_FLAGS -encoding UTF-8 -Xlint:all -cp "out/classes:lib/*" \
	    -d out/test-classes @out/test-sources.txt
	touch "$@"

RUN_TESTS := \
	. scripts/java-env.sh ; \
	exec "$$JAVA" -cp "out/classes:out/test-classes:lib/*" net.nuke24.togetl.test.TestMain

out/.tests-ran: out/.test-classes-built
	${RUN_TESTS}
	touch "$@"

.PHONY: run-tests
run-tests: out/.test-classes-built
	${RUN_TESTS}

out/3dparts-data.togetltsv: fixtures/upstream/3DPrintingData.tef out/.main-classes-built 
	./togetl import --dialect=parts \
		--input="$<" \
		--derive-source-uri=bitprint \
		--output="$@" \
		--report=out/3dparts-data-import.report.tev

.PHONY: load-3dparts-data
load-3dparts-data: out/3dparts-data.togetltsv out/.main-classes-built 
	./togetl load --db out/thedb.sqlite3 \
		--input out/3dparts-data.togetltsv \
		--report out/3dparts-data-load.report.tsv

.PHONY: run-web-server
run-web-server: load-3dparts-data
	TOGETL_DB=out/thedb.sqlite3 php -S localhost:8080 web/router.php
