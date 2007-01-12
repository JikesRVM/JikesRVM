#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

include                 $(RVM_BUILD)/Make.rules.host

default:	$(GEN_FILES_DIR)/com/ibm/jikesrvm/ia32/VM_Assembler.java

$(GEN_FILES_DIR)/com/ibm/jikesrvm/ia32/VM_Assembler.java:	VM_Assembler.in genAssembler.sh
	mkdir -p $(GEN_FILES_DIR)/com/ibm/jikesrvm/ia32
	$(BASH) genAssembler.sh $(GEN_FILES_DIR)/com/ibm/jikesrvm/ia32/VM_Assembler.java VM_Assembler.in

clean:
	rm -f $(GEN_FILES_DIR)/com/ibm/jikesrvm/ia32/VM_Assembler.java
