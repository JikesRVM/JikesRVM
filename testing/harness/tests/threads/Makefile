#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/testing/tests/threads
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=150
NON_DETERMINISTIC_OUTPUT_ORDER=YES
TIME_LIMIT=300

include		$(JAL_ROOT)/testing/harness/Make.rules

TESTS=TestNotification TestDispatch TestTimedWait TestInterruptAndSpin TestInterruptedSleep TestInterruptedWait

$(TESTS): %:
	$(MAKE) START_NAME=$@ do-sanity-compare

sanity:		$(TESTS)
	$(MAKE) do-sanity-finish
