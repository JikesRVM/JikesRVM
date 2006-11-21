#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
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

include         	$(RVM_BUILD)/Make.rules.target

BENCH_NAME=JLex
include			$(JAL_ROOT)/testing/harness/Local.rules

BENCH_SOURCE_PATH=$(BENCH_HOME)/classes 
BENCH_CLASS_PATH=$(JAVACUP_HOME)
HEAPSIZE=200
START_NAME=JLex.Main
TIME_LIMIT=600

include			$(JAL_ROOT)/testing/harness/Make.rules

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
