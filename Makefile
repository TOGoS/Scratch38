SHELL := bash
export TOGETL_ROOT := .

.DELETE_ON_ERROR:

.PHONY: all
all: out/.tests-ran out/thedb.sqlite3 out/vocab-report.tsv
	@echo '`make run-tests` to manually run tests again'

.PHONY: clean
clean:
	rm -rf out

# Demo fixtures are committed so builds are reproducible.
# They are syntax-fixed copies of upstream documents; the upstream
# originals (fetched by scripts/fetch-fixtures.sh) are not used here.
DEMO_FIXTURES := fixtures/demo

# Pinned by version and SHA-256 in scripts/get-sqlite-jdbc.sh.
LIBS := lib/sqlite-jdbc-3.46.1.3.jar

$(LIBS):
	scripts/get-sqlite-jdbc.sh

MAPPINGS := $(wildcard mappings/*.tsv)
VOCAB_FILES := $(wildcard vocab/*.tef)

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

# scripts/run-tests is the way to pass arguments to the test runner.
.PHONY: run-tests
run-tests: out/.test-classes-built
	${RUN_TESTS}

# Import reports are written alongside their TOGETLTSV output.
# Grouped targets (&:) need GNU Make 4.3 or later.
out/3DPrintingData.togetltsv out/3DPrintingData.report.tsv &: $(DEMO_FIXTURES)/3DPrintingData.tef $(MAPPINGS) out/.main-classes-built
	./togetl import --dialect=parts \
		--input="$<" \
		--derive-source-uri=bitprint \
		--output="out/3DPrintingData.togetltsv" \
		--report="out/3DPrintingData.report.tsv"

out/namespaces.togetltsv: $(DEMO_FIXTURES)/namespaces.tef $(MAPPINGS) out/.main-classes-built
	./togetl import --dialect=namespaces \
		--input="$<" \
		--derive-source-uri=bitprint \
		--output="$@"

TOGETLTSV_INPUTS := out/3DPrintingData.togetltsv out/namespaces.togetltsv
IMPORT_REPORTS := out/3DPrintingData.report.tsv

out/vocab-report.tsv: $(TOGETLTSV_INPUTS) $(MAPPINGS) $(VOCAB_FILES) out/.main-classes-built
	./togetl vocab --report="$@" \
		$(foreach f,$(TOGETLTSV_INPUTS),--input="$(f)")

out/thedb.sqlite3: $(TOGETLTSV_INPUTS) $(IMPORT_REPORTS) $(VOCAB_FILES) out/.main-classes-built
	./togetl load --db="$@" \
		$(foreach f,$(TOGETLTSV_INPUTS),--input="$(f)") \
		$(foreach f,$(IMPORT_REPORTS),--report="$(f)")

.PHONY: load
load: out/thedb.sqlite3

.PHONY: run-web-server
run-web-server: out/thedb.sqlite3
	TOGETL_DB=out/thedb.sqlite3 php -S localhost:8080 web/router.php
