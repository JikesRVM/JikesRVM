#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules

BENCH_NAME=Soot
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=150

DRIVER_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/iterationDriver
BENCH_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
BENCH_CLASS_PATH=$(BENCH_HOME)/soot/classes:$(BENCH_HOME)/jasmin/classes

ifeq ($(MODE),PERFORMANCE)
  ITERATIONS=2
  START_ARGS=$(ITERATIONS) soot.Main main -W --app -t -d cp --soot-classpath $(SPECjvm98_HOME):$(HOST_REPOSITORIES) SpecApplication --jimple
else
  ITERATIONS=1
  START_ARGS=$(ITERATIONS) soot.Main main -W --app -t -d cp --soot-classpath cp:$(HOST_REPOSITORIES) Driver --jimple
endif

START_NAME=Driver

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity:		sanity-check-rule


do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/soot/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "Soot has run for" < $(OUT)
