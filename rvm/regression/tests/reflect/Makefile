#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#
include $(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/reflect
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=100
LARGEHEAPSIZE=30

include $(JAL_ROOT)/rvm/regression/Make.rules

TESTS=tArray tClass tField tInstance

sanity: $(TESTS)
	$(MAKE) do-sanity-finish

$(TESTS): %:
	$(MAKE) START_NAME=$@ do-sanity-compare

