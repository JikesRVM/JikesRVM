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
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=100

ALL_CLASSES	= jgfutil/JGFInstrumentor \
                  jgfutil/JGFSection1 \
                  jgfutil/JGFSection2 \
                  jgfutil/JGFSection3 \
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

DRIVER_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/iterationDriver
BENCH_SOURCE_PATH=$(BENCH_HOME):$(DRIVER_SOURCE_PATH):$(BENCH_HOME)/section1
START_NAME=Driver
START_ARGS=2 JGFAll main

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class)
	$(MAKE) sanity-check-rule

else

BENCH_SOURCE_PATH=$(BENCH_HOME):$(BENCH_HOME)/section1
START_NAME=JGFAll
START_ARGS=

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class)
	$(MAKE) sanity-check-rule

endif

include		$(JAL_ROOT)/rvm/regression/Make.rules


do-gather-performance:

check:
