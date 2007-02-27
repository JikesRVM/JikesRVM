#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
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
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=150
TIME_LIMIT=300
EXPECTED=Tests.expected
START_NAME=TestAgent
AGENT=$(WORKING)/cp/Agent1.jar
BENCH_JDK_ARGS=-javaagent:$(AGENT)="Option1"
BENCH_RVM_ARGS=$(BENCH_JDK_ARGS)

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity:	$(AGENT)
	$(MAKE) TEST_NAME=javaagent sanity-diff

$(AGENT): Agent1.class
	$(JAR) cfm $@ $(BENCH_SOURCE_PATH)/MANIFEST.MF -C $(WORKING)/cp Agent1.class

$(TESTS): $(AGENT)
	$(MAKE) START_NAME=$@ do-sanity-compare

