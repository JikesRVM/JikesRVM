#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

include         	$(RVM_BUILD)/Make.rules.target

BENCH_NAME=JLex
include			$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_SOURCE_PATH=$(BENCH_HOME)/classes 
BENCH_CLASS_PATH=$(JAVACUP_HOME)
HEAPSIZE=200
START_NAME=JLex.Main

include			$(JAL_ROOT)/rvm/regression/Make.rules

ifeq ($(WORKING),.)
$(WORKING)/%.lex:	$(QBJC_HOME)/qb1.lex
else
$(WORKING)/%.lex:	$(QBJC_HOME)/qb1.lex $(WORKING)
endif
	cp $< $@

ifeq (x$(BOOTNAME),x)
sanity:			$(WORKING)/qb1.lex
else
sanity:			$(WORKING)/qb1.$(BOOTNAME).lex
endif
	$(MAKE) START_ARGS=$< sanity-check-rule

check:
	@diff $(START_ARGS).java qb1.lex.ref.classpath
