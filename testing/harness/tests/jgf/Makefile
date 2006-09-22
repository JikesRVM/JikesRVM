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

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=JGF
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=100

ALL_CLASSES	= jgfutil/JGFInstrumentor \
                  jgfutil/JGFSection1 \
                  jgfutil/JGFSection2 \
                  jgfutil/JGFSection3 \
                  jgfutil/JGFTimer \
                  JGFAll \
                  JGFArithBench \
                  JGFAssignBench \
                  JGFCastBench \
                  JGFCreateBench \
                  JGFExceptionBench \
                  JGFLoopBench \
                  JGFMathBench \
                  JGFMethodBench \
                  JGFSerialBench

ifeq ($(MODE),PERFORMANCE)

DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
BENCH_SOURCE_PATH=$(BENCH_HOME):$(DRIVER_SOURCE_PATH):$(BENCH_HOME)/section1
START_NAME=Driver
START_ARGS=2 JGFAll main
TIME_LIMIT=7200

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class)
	$(MAKE) sanity-check-rule

else

BENCH_SOURCE_PATH=$(BENCH_HOME):$(BENCH_HOME)/section1
START_NAME=JGFAll
START_ARGS=
TIME_LIMIT=3600

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class)
	$(MAKE) sanity-check-rule

endif

include		$(JAL_ROOT)/testing/harness/Make.rules


do-gather-performance:

check:
