#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=SPECjvm98
include		$(JAL_ROOT)/testing/harness/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)
START_NAME=SpecApplication

ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE=512
  INITIAL_HEAPSIZE=256
  TIME_LIMIT=3000
else
  HEAPSIZE=200
  TIME_LIMIT=600
endif

include 	$(JAL_ROOT)/testing/harness/Make.rules

TESTS=_200_check _201_compress _202_jess _209_db _213_javac _222_mpegaudio _227_mtrt _228_jack


$(WORKING)/spec:	$(WORKING)
	@rm -f $(WORKING)/spec
	@ln -fs $(BENCH_HOME)/spec $(WORKING)/spec

$(WORKING)/props:	$(WORKING)
	@rm -f $(WORKING)/props
	@ln -fs $(BENCH_HOME)/props $(WORKING)/props

check:
	@exit `$(FGREP) "Finished" $(OUT) | $(FGREP) "**NOT" $(OUT) | wc -l`
	@$(FGREP) "Finished" $(OUT) > /dev/null
	@$(FGREP) "Finished" $(OUT) | $(FGREP) -v "**NOT" $(OUT) > /dev/null
	@$(AWK) '/start\./{cnt++};/^-- Stack --/{cnt++};/Finished/{cnt--};END{exit cnt}' $(OUT)

ifeq ($(MODE),PERFORMANCE)

sanity:	$(WORKING)/spec $(WORKING)/props 
	@$(MAKE) START_ARGS="-a -g -m20 -M20 -s100 $(TESTS)" OUT=$(OUT) sanity-check-rule

do-gather-performance:
	$(JAL_ROOT)/testing/tests/SPECjvm98/parseSPECmark $(OUT) >> $(PERF_LOG)

else
ifeq ($(MODE),MEASURE_COMPILATION)

sanity:	$(WORKING)/spec $(WORKING)/props 
	@$(MAKE) START_ARGS="-a -g -m1 -M1 -s10 $(TESTS)" OUT=$(OUT) sanity-check-rule

else

$(TESTS): %: $(WORKING)/spec $(WORKING)/props 
	@$(MAKE) TEST_NAME="SPECjvm98 $@" START_ARGS="-a -d3000 -g -m2 -M4 -s10 $@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) do-sanity-finish

endif
endif

