#
# (C) Copyright IBM Corp. 2001, 2004
#
#$Id$
# @author John Whaley            
# @author Igor Pechtchanski      

## JCOMPILE is passed in here as an argument to the invocation of "make".

include			$(RVM_BUILD)/Make.rules.host

default:	tool

tool:		GenerateFromTemplate.java
		$(JCOMPILE) -d $(SCRATCH_DIR) $<
