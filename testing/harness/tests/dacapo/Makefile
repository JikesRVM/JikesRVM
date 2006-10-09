# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Robin Garner
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Dacapo
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=150

START_NAME=Harness

ifeq ($(MODE),PERFORMANCE)
  TIME_LIMIT=1800
else
  TIME_LIMIT=480
endif

include		$(JAL_ROOT)/testing/harness/Make.rules

TESTS = antlr bloat chart eclipse fop hsqldb jython luindex lusearch pmd xalan

Harness.class : $(JARFILE)
	:

check:
	@$(JAL_ROOT)/testing/harness/tests/dacapo/sanity-check2.sh $(OUT) $(START_ARGS)


$(TESTS): %:
	@$(MAKE) START_ARGS="$@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) START_NAME="DaCapo" do-sanity-finish
