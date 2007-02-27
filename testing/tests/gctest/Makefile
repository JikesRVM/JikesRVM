
#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
#
# @author Perry Cheng
#

SUPPORT =Node2I2A
TESTS =ReferenceTest FixedLive LargeAlloc Exhaust

include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=./src
BENCH_SOURCE_PATH=$(BENCH_HOME)
ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE   = 300
  START_ARGS = perf
  TIME_LIMIT = 1200
else
  START_ARGS = base
  TIME_LIMIT = 300
endif
EXPECTED=
BENCH_RVM_ARGS=

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity:
	$(MAKE) InlineAllocation HEAPSIZE=60
	$(MAKE) ReferenceTest HEAPSIZE=60
	$(MAKE) FixedLive     HEAPSIZE=120
	$(MAKE) LargeAlloc    HEAPSIZE=60
	$(MAKE) Exhaust       HEAPSIZE=50
	echo DONE

$(TESTS): %:
	$(MAKE) TEST_NAME="gctest $@" START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "Overall:"'

$(WORKING)/cp/InlineAllocation.class: $(BENCH_HOME)/InlineAllocation.java
	$(RVM_BUILD)/jbuild.toolPrep --disable-modification-exit-status /tmp $(BENCH_HOME)/InlineAllocation.java 
	mv /tmp/InlineAllocation.java $(WORKING)/cp/InlineAllocation.java
	$(RVM_BUILD)/jbuild.tool $(WORKING)/cp/InlineAllocation.java

InlineAllocation: $(WORKING)/cp $(WORKING)/cp/InlineAllocation.class 
	$(MAKE) TEST_NAME="gctest InlineAllocation" START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "SUCCESS"'

check:
	@$(MY_RULE) < $(OUT)

do-gather-performance:
	@$(FGREP) -q "Overall:" $(OUT) >> $(PERF_LOG)



