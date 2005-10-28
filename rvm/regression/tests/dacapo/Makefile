#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Robin Garner
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Dacapo
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=150

# 
# We know that batik and chart both fail - add them into the list
# when classpath problems are fixed.
#
#TESTS	= antlr batik bloat chart fop hsqldb jython pmd ps xalan
TESTS	= antlr bloat fop hsqldb jython pmd ps xalan

DRIVER_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/iterationDriver
#BENCH_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
#BENCH_CLASS_PATH=
START_NAME=Harness
START_ARGS=$(TESTS)

sanity: 
	$(MAKE) sanity-check-rule

include		$(JAL_ROOT)/rvm/regression/Make.rules

Harness.class : $(JARFILE)
	:

do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/dacapo/perf.awk $(OUT) >> $(PERF_LOG)

$(TESTS): %: $(WORKING)/Makefile
        cd $(WORKING); $(MAKE) START_ARGS="$@" OUT=$(OUT).$@ do-sanity-check-rule


check:
	@ $(JAL_ROOT)/rvm/regression/tests/dacapo/sanity-check.sh $(OUT) $(TESTS)


