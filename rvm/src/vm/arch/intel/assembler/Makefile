#
# (C) Copyright IBM Corp. 2001
#
include                 $(JAL_ROOT)/build.Make.rules

default:	$(GEN_FILES_DIR)/VM_Assembler.java

$(GEN_FILES_DIR)/VM_Assembler.java:	VM_Assembler.in genAssembler.sh
	cat VM_Assembler.in > $(GEN_FILES_DIR)/VM_Assembler.java
	$(BASH) genAssembler.sh >> $(GEN_FILES_DIR)/VM_Assembler.java
	echo "}" >> $(GEN_FILES_DIR)/VM_Assembler.java

clean:
	rm -f $(GEN_FILES_DIR)/VM_Assembler.java
