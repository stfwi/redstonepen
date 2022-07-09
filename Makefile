# @file Makefile
# @author Stefan Wilhelm (wile)
# @license MIT
#
# GNU Make makefile based build relay.
# Note for reviewers/clones: This file is a auxiliary script for my setup.
# It's not needed to build the mod.
#
MOD_JAR_PREFIX=redstonepen-
MOD_JAR=$(filter-out %-sources.jar,$(wildcard build/libs/${MOD_JAR_PREFIX}*.jar))

ifeq ($(OS),Windows_NT)
GRADLE=gradlew.bat --no-daemon
GRADLE_STOP=gradlew.bat --stop
else
GRADLE=./gradlew --no-daemon
GRADLE_STOP=./gradlew --stop
endif
TASK=djs ../../zmeta/lib/tasks.js

wildcardr=$(foreach d,$(wildcard $1*),$(call wildcardr,$d/,$2) $(filter $(subst *,%,$2),$d))

#
# Targets
#
.PHONY: default mod data init clean clean-all mrproper all run install sanitize dist-check dist start-server assets

default: mod

all: clean clean-all mod | install

mod:
	@echo "[1.19] Building mod using gradle ..."
	@$(GRADLE) build $(GRADLE_OPTS)

assets:
	@echo "[1.19] Running asset generators ..."
	@$(TASK) assets

data:
	@echo "[1.19] Running data generators ..."
	@$(TASK) datagen

clean:
	@echo "[1.19] Cleaning ..."
	@rm -rf src/generated
	@rm -rf mcmodsrepo
	@rm -f build/libs/*
	@$(GRADLE) clean

clean-all:
	@echo "[1.19] Cleaning using gradle ..."
	@rm -rf mcmodsrepo
	@rm -f dist/*
	@rm -rf build/
	@rm -rf out/
	@rm -rf logs/
	@rm -rf run/logs/
	@rm -rf run/crash-reports/
	@rm -rf remappedSrc
	@$(GRADLE) clean

mrproper: clean-all
	@rm -f meta/*.*
	@rm -rf run/
	@rm -f .project
	@rm -f .classpath

init:
	@echo "[1.19] Initialising eclipse workspace using gradle ..."
	@$(GRADLE) genSources

sanitize:
	@echo "[1.19] Running sanitising tasks ..."
	@$(TASK) sanitize
	@$(TASK) version-check
	@$(TASK) update-json
	@git status -s .

dist-check:
	@echo "[1.19] Running dist checks ..."
	@$(TASK) dist-check

dist-files: clean init mod
	@echo "[1.19] Distribution files ..."
	@mkdir -p dist
	@cp build/libs/$(MOD_JAR_PREFIX)* dist/
	@rm -f dist/*-sources.jar
	@$(TASK) dist

dist: sanitize dist-check dist-files

run:
	@$(GRADLE) runClient
