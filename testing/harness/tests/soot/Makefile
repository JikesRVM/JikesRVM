#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Soot
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=300

BENCH_CLASS_PATH=$(BENCH_HOME)/soot/classes:$(BENCH_HOME)/jasmin/classes
BENCH_SOURCE_PATH=$(JAL_ROOT)/testing/harness/tests/soot
START_NAME=soot.Main

JCOMPILE=$(RVM_BUILD)/jbuild.tool

ifeq ($(MODE),PERFORMANCE)
  START_ARGS=-O --app -time -d cp --soot-classpath cp:$(SPECjvm98_HOME):$(RVM_BUILD)/RVM.classes/rvmrt.jar:$(RVM_BUILD)/RVM.classes/jksvm.jar SpecApplication -f J
  TIME_LIMIT=900
else
  START_ARGS=-O -app -time -d cp --soot-classpath cp:$(RVM_BUILD)/RVM.classes/rvmrt.jar:$(RVM_BUILD)/RVM.classes/jksvm.jar Hello -f J
  TIME_LIMIT=600
endif


include		$(JAL_ROOT)/testing/harness/Make.rules


sanity:		$(WORKING)/Hello.class sanity-check-rule


do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/testing/harness/tests/soot/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "Soot has run for" < $(OUT)

$(WORKING)/Hello.java:		$(JAL_ROOT)/testing/harness/tests/soot/Hello.java $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif
