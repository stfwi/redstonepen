#--------------------------------------------------------------------------------
#
#--------------------------------------------------------------------------------
# Config
#--------------------------------------------------------------------------------
TOOLCHAIN=
STD=c++17
CXX=$(TOOLCHAIN)g++
LD=$(CXX)
FLAGSCXX=-std=$(STD) -W -Wall -Wextra -pedantic
INCLUDES=-I./include
GIT_COMMIT_VERSION:=$(shell git log --pretty=format:%h -1 2>/dev/null || /bin/true)
BUILDDIR=./build
PROGRAM_NAME=rca-test
#--------------------------------------------------------------------------------
MAKEFLAGS+=--no-print-directory --output-sync=target --no-builtin-rules

# Header dependencies
wildcardr=$(foreach d,$(wildcard $1*),$(call wildcardr,$d/,$2) $(filter $(subst *,%,$2),$d))
HEADER_DEPS=$(sort $(call wildcardr, $(subst -I, ,$(INCLUDES)), *.hh))

# Make command line overrides
FLAGSCXX+=$(FLAGS)
FLAGSCXX+=$(INCLUDES) $(CXXFLAGS)
FLAGSLD+=$(LDFLAGS)

ifeq ($(OS),Windows_NT)
 PLATFORM:=win32
 BINARY_EXTENSION=.exe
 FLAGSCXX+=-D_WIN32_WINNT=0x0601 -DWINVER=0x0601 -D_WIN32_IE=0x0900
 LDSTATIC+=-static -static-libstdc++ -static-libgcc
 LIBS+=-ladvapi32 -lshell32 -lpthread
else
 # Only platform/linux/include relevant.
 PLATFORM:=linux
 BINARY_EXTENSION=
 LIBS+=-lrt -lpthread
 ifdef STATIC
  LDSTATIC+=-static -static-libstdc++ -static-libgcc
 endif
endif

#--------------------------------------------------------------------------------
# Targets
#--------------------------------------------------------------------------------
.PHONY: all binary run clean mrproper

all: binary

clean:
	@echo "[note] Cleaning"
	@rm -rf ./build

mrproper: clean

binary: $(BUILDDIR)/$(PROGRAM_NAME)$(BINARY_EXTENSION)

run: binary
	@echo "[run ] Starting $(PROGRAM_NAME)$(BINARY_EXTENSION) ..."
	@cd $(BUILDDIR); ./$(PROGRAM_NAME)$(BINARY_EXTENSION) $(ARGS)


$(BUILDDIR)/$(PROGRAM_NAME)$(BINARY_EXTENSION): src/main.cc $(HEADER_DEPS)
	@echo "[c++ ] $@"
	@mkdir -p $(dir $@)
	@rm -f $(BUILDDIR)/$(PROGRAM_NAME)$(BINARY_EXTENSION)
	@$(CXX) -o $@ $< $(FLAGSCXX) -I. $(INCLUDES) $(FLAGSLD) $(LDSTATIC) $(LIBS) $(OPTS)

#--
