#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# @author Julian Dolby

include                 $(RVM_BUILD)/Make.rules.host

default:	$(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java $(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_HeapLayoutConstants.java $(GEN_FILES_DIR)/com/ibm/jikesrvm/memorymanagers/mminterface/Selected.java $(GEN_FILES_DIR)/com/ibm/jikesrvm/ArchitectureSpecific.java

$(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java:	VM_Configuration.java
	cp $< $@ 

$(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_HeapLayoutConstants.java:	VM_HeapLayoutConstants.java
	cp $< $@ 

$(GEN_FILES_DIR)/com/ibm/jikesrvm/ArchitectureSpecific.java:	ArchitectureSpecific.java
	cp $< $@ 

$(GEN_FILES_DIR)/com/ibm/jikesrvm/memorymanagers/mminterface/Selected.java:	Selected.java
	mkdir -p $(GEN_FILES_DIR)/com/ibm/jikesrvm/memorymanagers/mminterface
	cp $< $@ 

clean:
	rm -f $(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_Configuration.java $(GEN_FILES_DIR)/com/ibm/jikesrvm/VM_HeapLayoutConstants.java $(GEN_FILES_DIR)/com/ibm/jikesrvm/memorymanagers/mminterface/Selected.java