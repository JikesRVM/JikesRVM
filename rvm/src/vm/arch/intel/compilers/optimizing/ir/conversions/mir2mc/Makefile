#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# @author Julian Dolby

include $(RVM_BUILD)/Make.rules.host

ARCH_IFS = $(JAL_ROOT)/rvm/src/vm/arch/intel/compilers/optimizing/ir/instruction/InstructionFormatList.dat
ARCH_OPS = $(JAL_ROOT)/rvm/src/vm/arch/intel/compilers/optimizing/ir/instruction/OperatorList.dat

SCRATCH_DIR=$(RVM_BUILD)/RVM.scratch

GENERATE = $(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) GenerateFromTemplate

TABLES_JAVA=$(SCRATCH_DIR)/OPT_InstructionFormatTables.java
TABLES_CLASS=$(SCRATCH_DIR)/OPT_InstructionFormatTables.class

OPCODE_JAVA=$(SCRATCH_DIR)/OPT_OperatorFormatTables.java
OPCODE_CLASS=$(SCRATCH_DIR)/OPT_OperatorFormatTables.class

VM_ASM_JAVA=$(SCRATCH_DIR)/com/ibm/JikesRVM/VM_Assembler.java
VM_ASM_CLASS=$(SCRATCH_DIR)/com/ibm/JikesRVM/VM_Assembler.class

FAKES =	VM_CodeArray.fake VM_Lister.fake VM.fake VM_Magic.fake VM_MachineCode.fake VM_Compiler.fake
FAKE_JAVA = $(patsubst %.fake,%.java,$(FAKES)) NoInlinePragma.java Offset.java 
FAKE_SRC = $(patsubst %.fake,$(SCRATCH_DIR)/com/ibm/JikesRVM/%.java,$(FAKES)) \
           $(SCRATCH_DIR)/org/vmmagic/pragma/NoInlinePragma.java \
	   $(SCRATCH_DIR)/org/vmmagic/unboxed/Offset.java
FAKE_CLS = $(patsubst %.java,%.class,$(FAKE_SRC)) \
	   $(SCRATCH_DIR)/com/ibm/JikesRVM/VM_CodeArray\$$Factory.class


SRCS=   $(TABLES_JAVA) \
	$(OPCODE_JAVA) \
	$(FAKE_SRC) \
	$(VM_ASM_JAVA) \
	$(JAL_ROOT)/rvm/src/vm/arch/intel/VM_RegisterConstants.java \
	$(JAL_ROOT)/rvm/src/vm/compilers/baseline/VM_ForwardReference.java \
	$(JAL_ROOT)/rvm/src/vm/arch/intel/assembler/VM_AssemblerConstants.java

default:		assembler clean

$(VM_ASM_JAVA):
	$(CP) $(GEN_FILES_DIR)/VM_Assembler.java $(VM_ASM_JAVA)

$(TABLES_JAVA):		OPT_InstructionFormatTables.template $(ARCH_IFS)
	$(GENERATE) OPT_InstructionFormatTables.template $(TABLES_JAVA) FORMAT_FILE=$(ARCH_IFS)

$(OPCODE_JAVA):		OPT_OperatorFormatTables.template $(ARCH_OPS)
	$(GENERATE) OPT_OperatorFormatTables.template $(OPCODE_JAVA) OPERATOR_FILE=$(ARCH_OPS)

$(SCRATCH_DIR)/com/ibm/JikesRVM/%.java:	%.fake
	$(CP) $< $@

$(SCRATCH_DIR)/org/vmmagic/pragma/%.java:	%.fake
	$(CP) $< $@

$(SCRATCH_DIR)/org/vmmagic/unboxed/%.java:	%.fake
	$(CP) $< $@

assembler:		$(GEN_FILES_DIR)/OPT_Assembler.java

$(SCRATCH_DIR)/GenerateAssembler.java:	GenerateAssembler.java
	$(CP) GenerateAssembler.java $(SCRATCH_DIR) 

$(GEN_FILES_DIR)/OPT_Assembler.java:	Makefile $(SCRATCH_DIR)/GenerateAssembler.java $(SRCS)
	$(JCOMPILE) -d $(SCRATCH_DIR) $(SRCS) $(SCRATCH_DIR)/GenerateAssembler.java
	$(HOST_VM_RT) -cp $(SCRATCH_DIR) -DgenerateToDir=$(GEN_FILES_DIR) GenerateAssembler

clean:
	$(RM) $(FAKE_SRC) $(FAKE_CLS) 
