#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

include                 $(JAL_ROOT)/rvm/build.Make.rules

default:	$(GEN_FILES_DIR)/VM_Assembler.java

$(GEN_FILES_DIR)/VM_Assembler.java:	VM_Assembler.in genAssembler.sh
	$(BASH) genAssembler.sh $(GEN_FILES_DIR)/VM_Assembler.java VM_Assembler.in

clean:
	rm -f $(GEN_FILES_DIR)/VM_Assembler.java
