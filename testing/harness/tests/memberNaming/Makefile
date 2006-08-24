#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Dave Grove
#
include $(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/testing/tests/memberNaming
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=150
TIME_LIMIT=300

include $(JAL_ROOT)/testing/harness/Make.rules

TESTS=ResolvedAccess

sanity: $(TESTS)
	$(MAKE) do-sanity-finish

$(TESTS): %:
	$(MAKE) START_NAME=$@ do-sanity-compare

