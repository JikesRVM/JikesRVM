#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules

BENCH_NAME=Xerces
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=150

DRIVER_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/iterationDriver
BENCH_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
BENCH_CLASS_PATH=$(BENCH_HOME)/build/xercesImpl.jar:$(BENCH_HOME)/build/xercesSamples.jar:$(BENCH_HOME)/build/xmlParserAPIs.jar

## TODO: find a better input file
INPUT_FILE=$(BENCH_HOME)/build.xml

START_NAME=Driver
START_ARGS=2 dom.Counter main $(INPUT_FILE)

sanity:		sanity-check-rule

include		$(JAL_ROOT)/rvm/regression/Make.rules

do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/xerces/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "702 attrs" < $(OUT)
