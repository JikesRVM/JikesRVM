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
HEAPSIZE=200
START_NAME=arraymethod

include 	$(JAL_ROOT)/rvm/regression/Make.rules

check:
	@$(FGREP) "To see FPRs specify" $(OUT) > /dev/null 
	@exit `$(FGREP) "Macro line" $(OUT) | wc -l | xargs -i expr {} - 8`
	@exit `$(FGREP) "jid" $(OUT) | wc -l`

$(WORKING)/startup.jdp:		$(WORKING)
ifneq ($(WORKING),.)
	cp -f $(JAL_ROOT)/rvm/regression/tests/jdp/startup.jdp $(WORKING)
endif

sanity: $(WORKING)/startup.jdp
	$(MAKE) JBOOT=$(JAL_ROOT)/rvm/bin/jdp PROCESSORS=1 sanity-check-rule

