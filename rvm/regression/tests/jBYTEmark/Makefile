#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=jBYTEmark
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=150

ifeq ($(MODE),PERFORMANCE)

ALL_TESTS	= AssignmentTest   FourierTest      RandNum \
                  BMglobals        HuffmanTest      StringSortTest \
                  BitOpsTest       IDEATest         Wordlist \
                  BmarkTest        InternalFPF      huff_node \
                  ClockTest        LuTest           jBYTEmark \
                  EMFloatTest      NeuralNetTest \
                  EmFloatPnt       NumericSortTest


DRIVER_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/iterationDriver
BENCH_SOURCE_PATH=$(JAL_ROOT)/rvm/regression/tests/jBYTEmark:$(BENCH_HOME):$(DRIVER_SOURCE_PATH)
START_NAME=Driver
START_ARGS=2 jBYTEmark main

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-check-rule

else

BENCH_SOURCE_PATH=$(JAL_ROOT)/rvm/regression/tests/jBYTEmark:$(BENCH_HOME)
START_NAME=jBYTEmark
sanity:		sanity-check-rule

endif

include		$(JAL_ROOT)/rvm/regression/Make.rules


do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/jBYTEmark/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "FP Index:" < $(OUT)


