#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author John Whaley            
# @author Igor Pechtchanski      

include			$(RVM_BUILD)/Make.rules.host

default:	tool

tool:		GenerateFromTemplate.java
		$(JCOMPILE) -d $(SCRATCH_DIR) $<
