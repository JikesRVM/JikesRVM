#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Soot
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=300

BENCH_CLASS_PATH=$(BENCH_HOME)/soot/classes:$(BENCH_HOME)/jasmin/classes
BENCH_SOURCE_PATH=$(JAL_ROOT)/rvm/regression/tests/soot
START_NAME=soot.Main

ifeq ($(MODE),PERFORMANCE)
  START_ARGS=-W --app -t -d cp --soot-classpath $(SPECjvm98_HOME):$(HOST_REPOSITORIES) SpecApplication --jimple
else
  START_ARGS=-W --app -t -d cp --soot-classpath cp:$(HOST_REPOSITORIES) Hello --jimple
endif


include		$(JAL_ROOT)/rvm/regression/Make.rules


sanity:		Hello.class sanity-check-rule


do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/soot/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "Soot has run for" < $(OUT)
