#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Perry Cheng
#

SUPPORT =Node2I2A
TESTS =FixedLive LargeAlloc

include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/gctest
BENCH_SOURCE_PATH=$(BENCH_HOME)
ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE   = 300
  START_ARGS = perf
else
  HEAPSIZE   = 90
  START_ARGS = base
endif
EXPECTED=
BENCH_RVM_ARGS=

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity: 	$(TESTS)
	echo DONE

$(TESTS): %:
	$(MAKE) START_NAME=$@ sanity-check-rule

check:
	@$(FGREP) -q "Overall:" < $(OUT)




