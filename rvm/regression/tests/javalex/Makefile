#
# (C) Copyright IBM Corp. 2001
#
include         	$(RVM_BUILD)/Make.rules

BENCH_NAME=JLex
include			$(JAL_ROOT)/rvm/regression/Local.rules

JAVACUP_HOME=/homes/fury5/jalapeno/contrib/benchmarks/java_cup_v10j
QBCJ_HOME=/homes/fury5/jalapeno/contrib/benchmarks/QBJC
BENCH_SOURCE_PATH=$(BENCH_HOME)/classes 
BENCH_CLASS_PATH=$(JAVACUP_HOME)
HEAPSIZE=200
START_NAME=JLex.Main

include			$(JAL_ROOT)/rvm/regression/Make.rules

ifeq ($(WORKING),.)
$(WORKING)/%.lex:	$(QBCJ_HOME)/qb1.lex
else
$(WORKING)/%.lex:	$(QBCJ_HOME)/qb1.lex $(WORKING)
endif
	cp $< $@

ifeq (x$(BOOTNAME),x)
sanity:			$(WORKING)/qb1.lex
else
sanity:			$(WORKING)/qb1.$(BOOTNAME).lex
endif
	$(MAKE) START_ARGS=$< sanity-check-rule

check:
	@diff $(START_ARGS).java qb1.lex.ref
