#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Perry Cheng
#

ALL_TESTS	= FixedLive Node2I2A

include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/gctest
BENCH_SOURCE_PATH=$(BENCH_HOME)
ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE   = 300
  START_ARGS = 100
else
  HEAPSIZE   = 100
  START_ARGS = 30
endif
EXPECTED=Tests.expected
START_NAME=FixedLive
BENCH_RVM_ARGS=

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-check-rule

check:
	@$(FGREP) -q "Overall Rate:" < $(OUT)

