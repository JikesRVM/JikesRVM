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
BENCH_CLASS_PATH=$(BENCH_HOME)/build/xercesImpl.jar:$(BENCH_HOME)/build/xercesSamples.jar:$(BENCH_HOME)/build/xmlParserAPIs.jar:$(XML_DATA)/shakespeare.1.10.xml.zip

INPUT_FILES=$(wildcard $(XML_DATA)/shakespeare.1.10.xml/*.xml)

TESTS=dom.Counter sax.Counter

ifeq ($(MODE),PERFORMANCE)
  ITERATIONS=2
else
  ITERATIONS=1
endif

$(TESTS): %: 
	 $(MAKE) START_NAME=Driver START_ARGS="$(ITERATIONS) $@ main $(INPUT_FILES)" OUT=$(OUT).$@ sanity-check-rule

sanity:		$(TESTS)
	 $(MAKE) do-sanity-finish

include		$(JAL_ROOT)/rvm/regression/Make.rules

do-gather-performance:  
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/xerces/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "136122 chars" < $(OUT)
