#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

include                 $(RVM_BUILD)/Make.rules.host

default:	$(GEN_FILES_DIR)/VM_Assembler.java

$(GEN_FILES_DIR)/VM_Assembler.java:	VM_Assembler.in genAssembler.sh
	$(BASH) genAssembler.sh $(GEN_FILES_DIR)/VM_Assembler.java VM_Assembler.in

clean:
	rm -f $(GEN_FILES_DIR)/VM_Assembler.java
