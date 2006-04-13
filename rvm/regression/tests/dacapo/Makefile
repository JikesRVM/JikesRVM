#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Robin Garner
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Dacapo
include		$(JAL_ROOT)/rvm/regression/Local.rules

HEAPSIZE=150

START_NAME=Harness

include		$(JAL_ROOT)/rvm/regression/Make.rules

# 
# We know that batik and chart both fail - add them into the list
# when classpath problems are fixed.
#
#TESTS	= antlr batik bloat chart fop hsqldb jython pmd ps xalan
TESTS	= antlr bloat fop hsqldb jython pmd ps xalan

Harness.class : $(JARFILE)
	:

$(WORKING)/Makefile:	$(WORKING) $(JAL_ROOT)/rvm/regression/tests/dacapo/Makefile
ifneq ($(WORKING),.)
	cp -f $(JAL_ROOT)/rvm/regression/tests/dacapo/Makefile $(WORKING)/Makefile
endif


check:
	@ $(JAL_ROOT)/rvm/regression/tests/dacapo/sanity-check2.sh $(OUT) $(START_ARGS)


$(TESTS): %: $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="$@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) START_NAME="DaCapo" do-sanity-finish
