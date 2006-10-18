#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
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
