#
# (C) Copyright IBM Corp. 2001
#
include $(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/src/examples/serialization
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=50
LARGEHEAPSIZE=30

include $(JAL_ROOT)/regression/Make.rules

TESTS=TestWrite TestRead

sanity: $(TESTS)
	$(MAKE) do-sanity-finish

$(TESTS): %:
	$(MAKE) START_NAME=$@ START_ARGS=out.TestWrite.$(BOOTNAME).jdk do-sanity-compare

