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
HEAPSIZE = 300
EXPECTED=Tests.expected
START_NAME=FixedLive
START_ARGS=100
BENCH_RVM_ARGS=

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-check-rule

check:
	@$(FGREP) -q "Overall Rate:" < $(OUT)

