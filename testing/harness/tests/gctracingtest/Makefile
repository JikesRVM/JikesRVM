#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#

TESTS = TracingTest

include         $(RVM_BUILD)/Make.rules.target

BENCH_HOME = $(JAL_ROOT)/testing/tests/gctracingtest
BENCH_SOURCE_PATH =$(BENCH_HOME)
EXPECTED =
BENCH_RVM_ARGS = "-X:sysLogfile=/dev/null"

TIME_LIMIT=600

include 	$(JAL_ROOT)/testing/harness/Make.rules

sanity:
	$(MAKE) TracingTest HEAPSIZE=200 PROCESSORS=1
	echo DONE

$(TESTS): %:
	$(MAKE) START_NAME=$@ sanity-check-rule MY_RULE='$(FGREP) -q "TracingTest Finished"'

check:
	@$(MY_RULE) < $(OUT)
