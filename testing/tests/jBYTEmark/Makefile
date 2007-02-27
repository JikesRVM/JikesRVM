#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
#
# @author Julian Dolby
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=jBYTEmark

include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=150

START_NAME=jBYTEmark
TIME_LIMIT=900

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity:		sanity-check-rule

jBYTEmark.class : $(JARFILE)
	:

check:
	@$(FGREP) -q "FP Index:" < $(OUT)


