#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author John Whaley            
# @author Igor Pechtchanski      

include			$(RVM_ROOT)/rvm/build.Make.rules

default:	tool

tool:		GenerateFromTemplate.java
		$(JCOMPILE) -d $(SCRATCH_DIR) $<
