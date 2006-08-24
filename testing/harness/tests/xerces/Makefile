#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Xerces
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=150

DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
BENCH_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
BENCH_CLASS_PATH=$(BENCH_HOME)/build/xercesImpl.jar:$(BENCH_HOME)/build/xercesSamples.jar:$(BENCH_HOME)/build/xmlParserAPIs.jar:$(XML_DATA)/shakespeare.1.10.xml.zip

INPUT_FILES=$(wildcard $(XML_DATA)/shakespeare.1.10.xml/*.xml)

TESTS=dom.Counter sax.Counter

ifeq ($(MODE),PERFORMANCE)
  ITERATIONS=2
  TIME_LIMIT=900
else
  ITERATIONS=1
  TIME_LIMIT=300
endif

$(TESTS): %: 
	 $(MAKE) START_NAME=Driver START_ARGS="$(ITERATIONS) $@ main $(INPUT_FILES)" OUT=$(OUT).$@ sanity-check-rule

sanity:		$(TESTS)
	 $(MAKE) do-sanity-finish

include		$(JAL_ROOT)/testing/harness/Make.rules

do-gather-performance:  
	 @$(AWK) -f $(JAL_ROOT)/testing/harness/tests/xerces/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "136122 chars" < $(OUT)
