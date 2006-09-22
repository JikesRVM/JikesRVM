#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id:
#
# @author Peter Sweeney

include		$(RVM_BUILD)/Make.rules.target
 
BENCH_NAME=pseudojbb
include		$(JAL_ROOT)/testing/harness/Local.rules

BENCH_JARS=jbb.jar jbb_no_precompile.jar check.jar reporter.jar
#BENCH_CLASS_PATH=$(foreach JAR, $(BENCH_JARS), $(BENCH_HOME)/$(JAR))
BENCH_CLASS_PATH=$(BENCH_HOME)/src

vpath %.jar	$(BENCH_HOME)

START_NAME=spec.jbb.JBBmain

INITIAL_HEAPSIZE=300
HEAPSIZE=600

ifeq ($(MODE),PERFORMANCE)
  TIME_LIMIT=2700
else
  TIME_LIMIT=1000
endif

include		$(JAL_ROOT)/testing/harness/Make.rules

#
# sanity test support
#

ifeq ($(MODE),PERFORMANCE)
sanity: 	$(WORKING)/pseudojbb.perf.props $(WORKING)/pseudojbb_config.props
	$(MAKE) START_ARGS="-propfile pseudojbb.perf.props" sanity-check-rule
else
sanity: 	$(WORKING)/pseudojbb.sanity.props $(WORKING)/pseudojbb_config.props
	$(MAKE) START_ARGS="-propfile pseudojbb.sanity.props" sanity-check-rule
endif


ifeq ($(MODE),PERFORMANCE)
check:
	@$(FGREP) -q "Finished time(secs):" < $(OUT)
else
check:
	@$(FGREP) -q "Finished time(secs):" < $(OUT)
endif

#
# performance runs
#
do-gather-performance:
	$(AWK) -f $(JAL_ROOT)/testing/harness/tests/pseudojbb/summarize.awk $(OUT) >> $(PERF_LOG)


#
# big runs to push performance off a cliff :)
#

bigrun:		$(WORKING)/pseudojbb.big.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.big.props" bench-rvm

bigrun-jdk:	$(WORKING)/pseudojbb.big.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.big.props" bench-jdk


#
# server application harness
#

$(RESULT): 
	$(MAKE) OUT=$(RESULT) 

start:

finish:

#
# misc.  
#
spec/jbb/JBBmain.class:		jbb.jar

$(WORKING)/%.props:		$(JAL_ROOT)/testing/harness/tests/pseudojbb/%.props $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif

