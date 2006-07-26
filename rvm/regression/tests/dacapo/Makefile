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

ifeq ($(MODE),PERFORMANCE)
  TIME_LIMIT=1800
else
  TIME_LIMIT=300
endif

include		$(JAL_ROOT)/rvm/regression/Make.rules

# 
# We know that batik and chart both fail - add them into the list
# when classpath problems are fixed.
#
#TESTS	= antlr batik bloat chart fop hsqldb jython pmd ps xalan
TESTS	= antlr bloat fop hsqldb jython pmd ps xalan

Harness.class : $(JARFILE)
	:

check:
	@$(JAL_ROOT)/rvm/regression/tests/dacapo/sanity-check2.sh $(OUT) $(START_ARGS)


$(TESTS): %:
	@$(MAKE) START_ARGS="$@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) START_NAME="DaCapo" do-sanity-finish
