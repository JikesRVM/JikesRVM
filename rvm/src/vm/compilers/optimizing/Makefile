#
# (C) Copyright IBM Corp. 2001, 2003
#
#$Id$
#
# @author Stephen Fink
# @author David Grove
include		$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:;	(cd ir/instruction && $(MAKE))
		(cd ir/conversions/lir2mir && $(MAKE))
		(cd driver && $(MAKE))
		(cd optimizations/local/instructionScheduler && $(MAKE))
		@echo -n "(opt templates expanded) "

