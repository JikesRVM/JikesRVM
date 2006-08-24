#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#
include $(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/testing/tests/reflect
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=150
TIME_LIMIT=300

include $(JAL_ROOT)/testing/harness/Make.rules

TESTS=tArray tClass tField tInstance

sanity: $(TESTS)
	$(MAKE) do-sanity-finish

$(TESTS): %:
	$(MAKE) START_NAME=$@ EXPECTED=$@.expected sanity-diff
