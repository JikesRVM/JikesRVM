#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/jdp
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=100
START_NAME=arraymethod
JBOOT=$(JAL_ROOT)/rvm/bin/jdp

include 	$(JAL_ROOT)/rvm/regression/Make.rules

check:
	@$(FGREP) "To see FPRs specify" $(OUT) > /dev/null 
	@exit `$(FGREP) "Macro line" $(OUT) | wc -l | xargs -i expr {} - 8`
	@exit `$(FGREP) "jid" $(OUT) | wc -l`

sanity:
	cd $(WORKING); $(MAKE) START_ARGS="$(START_NAME)" OUT=$(OUT) sanity-check-rule


