#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id: Makefile 10738 2006-08-24 18:29:58Z dgrove-oss $
#
# @author Julian Dolby
#
include $(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/testing/tests/nio
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=50
TIME_LIMIT=300

include $(JAL_ROOT)/testing/harness/Make.rules

TESTS=TestFileChannel

sanity: $(TESTS)
	$(MAKE) do-sanity-finish

$(TESTS): %:
	$(MAKE) TEST_NAME="nio $@" START_NAME=$@ START_ARGS=out.$@.$(BOOTNAME).jdk do-sanity-compare

