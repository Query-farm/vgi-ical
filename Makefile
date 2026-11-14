# vgi-ical VGI worker — build, unit-test, and SQL E2E targets.
#
# Usage:
#   make build      # fat JAR -> build/libs/vgi-ical-<ver>-all.jar
#   make test       # JUnit + SQL E2E (the full suite)
#   make test-unit  # ./gradlew test (JUnit only)
#   make test-sql   # shadowJar + fixtures + haybarn-unittest over test/sql/*
#
# SQL E2E needs haybarn-unittest on PATH:
#   uv tool install --prerelease allow 'haybarn-unittest>=2.0.0rc11,<2.1'   # ~/.local/bin/haybarn-unittest
#   export PATH="$$HOME/.local/bin:$$PATH"

GRADLE        ?= ./gradlew
UNITTEST      ?= haybarn-unittest

# The fat JAR is a self-contained VGI worker; its manifest sets Add-Opens so a
# bare `java -jar` works as a LOCATION (no extra --add-opens needed).
JAR_GLOB       = build/libs/vgi-ical-*-all.jar

.PHONY: test test-unit test-sql build fixtures clean

build:
	$(GRADLE) --no-daemon shadowJar

# Regenerate the committed SQL fixtures from the in-test iCalendar builder.
fixtures:
	$(GRADLE) --no-daemon generateSqlFixtures

test-unit:
	$(GRADLE) --no-daemon test

# SQL E2E: build the fat JAR, (re)generate fixtures, then run the .test files
# through DuckDB via haybarn-unittest with the worker as the VGI LOCATION. Goes
# through ci/run-integration.sh (as CI does), which rewrites each `require httpfs`
# into an explicit INSTALL+LOAD: run raw, haybarn-unittest SKIPS every file.
# TRANSPORT=subprocess|http|unix picks the worker transport (default subprocess).
test-sql: build fixtures
	@UT="$$(command -v $(UNITTEST))"; \
	if [ -z "$$UT" ]; then \
		echo "ERROR: $(UNITTEST) not found on PATH." >&2; \
		echo "  Install: uv tool install --prerelease allow 'haybarn-unittest>=2.0.0rc11,<2.1'" >&2; \
		echo "  Then:    export PATH=\"\$$HOME/.local/bin:\$$PATH\"" >&2; \
		exit 1; \
	fi; \
	JAR="$$(ls $(JAR_GLOB) | head -1)"; \
	if [ -z "$$JAR" ]; then echo "ERROR: fat JAR not built ($(JAR_GLOB))" >&2; exit 1; fi; \
	echo "Worker JAR: $$JAR"; \
	HAYBARN_UNITTEST="$$UT" \
	VGI_ICAL_WORKER="java -jar $$(pwd)/$$JAR" \
	VGI_ICAL_WORKER_JAR="$$(pwd)/$$JAR" \
		ci/run-integration.sh

test: test-unit test-sql

clean:
	$(GRADLE) --no-daemon clean
