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

include		$(RVM_BUILD)/Make.rules.target

BENCH_NAME=CaffeineMark
include		$(JAL_ROOT)/testing/harness/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=200
START_NAME=RunCaffeine

ifeq ($(MODE),PERFORMANCE)
  TIME_LIMIT=600
else
  TIME_LIMIT=300
endif

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity:		sanity-check-rule

check:
	@$(FGREP) -q "Method score:" $(OUT)

do-gather-performance:
	cat $(OUT) >> $(PERF_LOG)

