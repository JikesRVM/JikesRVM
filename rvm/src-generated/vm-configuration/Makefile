#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id: Makefile 11105 2006-12-01 16:26:36Z dgrove-oss $
#
# @author Julian Dolby

include                 $(RVM_BUILD)/Make.rules.host

default:	$(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java

$(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java:	VM_Configuration.java
	cp $< $@ 

clean:
	rm -f $(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java
