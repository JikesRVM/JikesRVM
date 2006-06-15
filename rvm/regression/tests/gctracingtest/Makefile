#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#

TESTS = TracingTest

include         $(RVM_BUILD)/Make.rules.target

BENCH_HOME = $(JAL_ROOT)/rvm/src/examples/gctracingtest
BENCH_SOURCE_PATH =$(BENCH_HOME)
EXPECTED =
BENCH_RVM_ARGS = "-X:sysLogfile=/dev/null"

include 	$(JAL_ROOT)/rvm/regression/Make.rules

sanity:
	$(MAKE) TracingTest HEAPSIZE=200 PROCESSORS=1
	echo DONE

$(TESTS): %:
	$(MAKE) START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "TracingTest Finished"'

check:
	@$(MY_RULE) < $(OUT)
