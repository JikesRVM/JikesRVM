#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

include         $(RVM_BUILD)/Make.rules

BENCH_NAME=jBYTEmark
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_SOURCE_PATH=$(JAL_ROOT)/rvm/regression/tests/jBYTEmark:$(BENCH_HOME)
HEAPSIZE=70
START_NAME=jBYTEmark

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity:		sanity-check-rule

do-gather-performance:
	awk -f $(JAL_ROOT)/rvm/regression/tests/jBYTEmark/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "FP Index:" < $(OUT)


