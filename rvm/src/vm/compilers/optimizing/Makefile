#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_ROOT)/build.Make.rules

MakeTemplates:;	(cd ir/instruction && $(MAKE))
		(cd ir/conversions/lir2mir && $(MAKE))
		(cd driver && $(MAKE))
		(cd optimizations/local/instructionScheduler && $(MAKE))
		@print -n "(opt templates expanded) "

