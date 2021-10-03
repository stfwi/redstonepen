# @file Makefile
# @author Stefan Wilhelm (wile)
# @license MIT
#
# GNU Make makefile based build relay.
# Note for reviewers/clones: This file is a auxiliary script for my setup.
# It's not needed to build the mod.
#
.PHONY: default init clean clean-all mrproper sanitize dist dist-all

default:	;	@echo "First change to specific version directory."
dist: default

clean:
	-@cd 1.16; make -s clean
	-@cd 1.17; make -s clean

clean-all:
	-@cd 1.16; make -s clean-all
	-@cd 1.17; make -s clean-all

mrproper:
	-@cd 1.16; make -s mrproper
	-@cd 1.17; make -s mrproper

sanitize:
	-@cd 1.16; make -s sanitize
	-@cd 1.17; make -s sanitize

init:
	-@cd 1.16; make -s init
	-@cd 1.17; make -s init

dist-all: clean-all init
	-@cd 1.16; make -s dist
	-@cd 1.17; make -s dist
