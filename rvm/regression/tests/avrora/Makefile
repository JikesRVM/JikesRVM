#
# (C) Copyright IBM Corp. 2005
#
# $Id$
#
# @author Michael Hind
#

include		$(RVM_BUILD)/Make.rules.target

BENCH_NAME=avrora
BENCH_SOURCE_PATH=$(BENCH_HOME)
BENCH_CLASS_PATH=$(BENCH_HOME)
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_JARS=avrora-beta-1.4.0.jar
BENCH_CLASS_PATH=$(foreach JAR, $(BENCH_JARS), $(BENCH_HOME)/$(JAR))

vpath %.jar	$(BENCH_HOME)

START_NAME=avrora.Main

ifeq ($(MODE),PERFORMANCE)
  START_ARGS="-colors=false" fibonacci-10.asm
else
  START_ARGS="-colors=false" fibonacci.asm
endif

include		$(JAL_ROOT)/rvm/regression/Make.rules

$(WORKING)/fibonacci.asm:	$(WORKING)
	 cp -f $(BENCH_SOURCE_PATH)/fibonacci.asm $(WORKING)/fibonacci.asm

$(WORKING)/fibonacci-10.asm:	$(WORKING)
	 cp -f $(BENCH_SOURCE_PATH)/fibonacci-10.asm $(WORKING)/fibonacci-10.asm

sanity: 	$(WORKING)/fibonacci.asm $(WORKING)/fibonacci-10.asm sanity-check-rule

do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/avrora/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) "Time for simulation" $(OUT) > /dev/null

avrora/Main.class:	avrora-beta-1.4.0.jar


