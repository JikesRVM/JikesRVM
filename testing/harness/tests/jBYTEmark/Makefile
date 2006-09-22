#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
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
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=150

ifeq ($(MODE),PERFORMANCE)

ALL_TESTS	= AssignmentTest   FourierTest      RandNum \
                  BMglobals        HuffmanTest      StringSortTest \
                  BitOpsTest       IDEATest         Wordlist \
                  BmarkTest        InternalFPF      huff_node \
                  ClockTest        LuTest           jBYTEmark \
                  EMFloatTest      NeuralNetTest \
                  EmFloatPnt       NumericSortTest


DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
BENCH_SOURCE_PATH=$(JAL_ROOT)/testing/harness/tests/jBYTEmark:$(BENCH_HOME):$(DRIVER_SOURCE_PATH)
START_NAME=Driver
START_ARGS=2 jBYTEmark main
TIME_LIMIT=600

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-check-rule

else

BENCH_SOURCE_PATH=$(JAL_ROOT)/testing/harness/tests/jBYTEmark:$(BENCH_HOME)
START_NAME=jBYTEmark
TIME_LIMIT=900

sanity:		sanity-check-rule

endif

include		$(JAL_ROOT)/testing/harness/Make.rules


do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/testing/harness/tests/jBYTEmark/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "FP Index:" < $(OUT)


