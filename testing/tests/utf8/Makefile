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
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=./src
BENCH_SOURCE_PATH=$(WORKING) $(BENCH_HOME)
HEAPSIZE=150
START_NAME=utf8test
TIME_LIMIT=300

include $(JAL_ROOT)/testing/harness/Make.rules

$(WORKING)/utf8test.java:	utf8testdriver.class
		$(HOST_VM_RT) -classpath $(BENCH_CLASS_CP) utf8testdriver > $(WORKING)/utf8test.java

sanity:		sanity-compare

fullclean:;	$(RM) utf8test.java utf8test.expected

