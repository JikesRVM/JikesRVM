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
# @author Peter Donald
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Veiovis
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=150
BENCH_SOURCE_PATH=$(BENCH_HOME)
START_NAME=Veiovis
TIME_LIMIT=120
EXPECTED=${BENCH_HOME}/Veiovis.out

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity:		sanity-diff

