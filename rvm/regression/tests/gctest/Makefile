
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Perry Cheng
#

SUPPORT =Node2I2A
TESTS =ReferenceTest FixedLive LargeAlloc Exhaust

include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/gctest
BENCH_SOURCE_PATH=$(BENCH_HOME)
ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE   = 300
  START_ARGS = perf
else
  START_ARGS = base
endif
EXPECTED=
BENCH_RVM_ARGS=

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity:
	$(MAKE) InlineAllocation HEAPSIZE=60
	$(MAKE) ReferenceTest HEAPSIZE=60
	$(MAKE) FixedLive     HEAPSIZE=120
	$(MAKE) LargeAlloc    HEAPSIZE=60
	$(MAKE) Exhaust       HEAPSIZE=50
	echo DONE

$(TESTS): %:
	$(MAKE) START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "Overall:"'

$(WORKING)/cp/InlineAllocation.class: $(BENCH_HOME)/InlineAllocation.java
	$(RVM_BUILD)/jbuild.toolPrep --disable-modification-exit-status /tmp $(BENCH_HOME)/InlineAllocation.java 
	mv /tmp/InlineAllocation.java $(WORKING)/cp/InlineAllocation.java
	$(RVM_BUILD)/jbuild.tool $(WORKING)/cp/InlineAllocation.java

InlineAllocation: $(WORKING)/cp $(WORKING)/cp/InlineAllocation.class 
	$(MAKE) START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "SUCCESS"'

check:
	@$(MY_RULE) < $(OUT)

do-gather-performance:
	@$(FGREP) -q "Overall:" $(OUT) >> $(PERF_LOG)



