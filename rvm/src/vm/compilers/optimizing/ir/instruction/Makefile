#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author David Grove
include                 $(RVM_BUILD)/Make.rules.host

default:		$(GEN_FILES_DIR)/OPT_Operator.java \
			$(GEN_FILES_DIR)/OPT_Operators.java \
			$(GEN_FILES_DIR)/OPT_OperatorNames.java \
			$(GEN_FILES_DIR)/InstructionFormats.RAW

GENERATE = $(HOST_VM_RT) -classpath $(SCRATCH_DIR) GenerateFromTemplate 

# make trickiness to allow generation of HIR/LIR without specifying an MIR.
# used in the opt compiler on JikesBT configuration
ifneq ($(ARCH_DIR),NONE)
ARCH_OPS = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction/OperatorList.dat
ARCH_IFS = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction/InstructionFormatList.dat
ARCH_IR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction/
ARCHITECTURE = $(ARCH_DIR)
else
ARCH_OPS =
ARCH_IFS =
ARCH_IR_DIR =
ARCHITECTURE = NONE
endif

RVMPACKAGE = com.ibm.JikesRVM.

$(GEN_FILES_DIR)/OPT_Operator.java: OPT_Operator.template  OperatorList.dat InstructionFormatList.dat $(ARCH_OPS) $(ARCH_IFS)
	$(GENERATE) OPT_Operator.template \
	$(GEN_FILES_DIR)/OPT_Operator.java \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)

$(GEN_FILES_DIR)/OPT_Operators.java: OPT_Operators.template OperatorList.dat InstructionFormatList.dat CommonOperands.dat $(ARCH_OPS) $(ARCH_IFS)
	$(GENERATE) OPT_Operators.template \
	$(GEN_FILES_DIR)/OPT_Operators.java \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS) \
	VERIFY_INSTRUCTION_FORMATS=""  ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)

$(GEN_FILES_DIR)/OPT_OperatorNames.java:	OPT_OperatorNames.template OperatorList.dat $(ARCH_OPS)
	$(GENERATE) OPT_OperatorNames.template \
	$(GEN_FILES_DIR)/OPT_OperatorNames.java \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)

$(GEN_FILES_DIR)/InstructionFormats.RAW: InstructionFormats.template OperatorList.dat InstructionFormatList.dat CommonOperands.dat $(ARCH_OPS) $(ARCH_IFS)
	$(GENERATE) InstructionFormats.template \
	$(GEN_FILES_DIR)/InstructionFormats.RAW \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS) \
	VERIFY_INSTRUCTION_FORMATS=""  ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)
	./splitInstructionFormats.perl $(GEN_FILES_DIR) $(GEN_FILES_DIR)/InstructionFormats.RAW

## TODO FIX these three rules!!
uncheckedInstructionFormat:	
	$(GENERATE) InstructionFormats.template \
	$(GEN_FILES_DIR)/InstructionFormats.RAW\
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS)
	VERIFY_INSTRUCTION_FORMATS="" ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)
	./splitInstructionFormats.perl $(GEN_FILES_DIR) $(GEN_FILES_DIR)/InstructionFormats.RAW

warnInstructionFormat:	
	$(GENERATE) InstructionFormats.template \
	$(GEN_FILES_DIR)/InstructionFormats.RAW\
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS)\
	VERIFY_INSTRUCTION_FORMATS="warn" ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)
	./splitInstructionFormats.perl $(GEN_FILES_DIR) $(GEN_FILES_DIR)/InstructionFormats.RAW

fatalInstructionFormat:	
	$(GENERATE) InstructionFormats.template \
	$(GEN_FILES_DIR)/InstructionFormats.RAW\
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS) \
	VERIFY_INSTRUCTION_FORMATS="fatal" ARCHITECTURE=$(ARCHITECTURE) \
	INSTRFMTPKG=com.ibm.JikesRVM.opt.ir RVMPKG=$(RVMPACKAGE)
	./splitInstructionFormats.perl $(GEN_FILES_DIR) $(GEN_FILES_DIR)/InstructionFormats.RAW

