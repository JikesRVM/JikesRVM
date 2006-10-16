#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2006
#
# $Id$
#
# @author Julian Dolby
# @author Dave Grove

include		$(RVM_BUILD)/Make.rules.target
 
BENCH_NAME=SPECjbb2005
include		$(JAL_ROOT)/testing/harness/Local.rules

BENCH_JARS=jbb.jar check.jar 
BENCH_CLASS_PATH=$(foreach JAR, $(BENCH_JARS), $(BENCH_HOME)/$(JAR))

vpath %.jar	$(BENCH_HOME)

START_NAME=spec.jbb.JBBmain

INITIAL_HEAPSIZE=256
ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE=600
  TIME_LIMIT=2700
else
  HEAPSIZE=400
  TIME_LIMIT=1000
endif

include		$(JAL_ROOT)/testing/harness/Make.rules

#
# sanity test support
#

ifeq ($(MODE),PERFORMANCE)
sanity: 	setup
	$(MAKE) START_ARGS="-propfile SPECjbb.perf.props" sanity-check-rule
else
sanity: 	setup
	$(MAKE) START_ARGS="-propfile SPECjbb.sanity.props" sanity-check-rule
endif


ifeq ($(MODE),PERFORMANCE)
check:
	@$(FGREP) -q "JVM Check OK" < $(OUT)
	@$(FGREP) -q "Run will be COMPLIANT" < $(OUT)
	@$(FGREP) -q "No errors.  Valid run." < $(OUT)
	@$(FGREP) -q "Valid run, Score is" < $(OUT)
else
check:
	@$(FGREP) -q "JVM Check OK" < $(OUT)
	@egrep -q "(Valid run, Score is)|(INVALID run; Score is Invalid, but estimate is)" < $(OUT)
endif

#
# performance runs
#
do-gather-performance:
	$(AWK) -f $(JAL_ROOT)/testing/harness/tests/SPECjbb2005/summarize.awk $(OUT) >> $(PERF_LOG)

#
# Misc setup
#
setup:	$(WORKING)/SPECjbb.sanity.props $(WORKING)/SPECjbb_config.props $(WORKING)/xml

spec/jbb/JBBmain.class:		jbb.jar

$(WORKING)/%.props:		$(JAL_ROOT)/testing/harness/tests/SPECjbb2005/%.props $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif

$(WORKING)/xml:	$(WORKING)
	@rm -f $(WORKING)/xml
	@ln -fs $(BENCH_HOME)/xml $(WORKING)/xml

