#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

include		$(RVM_BUILD)/Make.rules

BENCH_NAME=CaffeineMark
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=200
START_NAME=RunCaffeine

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity:		sanity-check-rule

check:
	@$(FGREP) -q "Method score:" $(OUT)

do-gather-performance:
	cat $(OUT) >> $(PERF_LOG)

