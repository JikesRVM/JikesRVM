#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Perry Cheng
#

ALL_TESTS	= FixedLive

include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/gctest
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE = 300
EXPECTED=Tests.expected
START_NAME=FixedLive
START_ARGS=100
BENCH_RVM_ARGS=-X:gc:verbose=1

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-diff


