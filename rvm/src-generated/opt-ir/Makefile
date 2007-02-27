#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# @author David Grove
include                 $(RVM_BUILD)/Make.rules.host

default:		$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operator.java \
			$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operators.java \
			$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_OperatorNames.java \
			$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/InstructionFormats.RAW

GENERATE = $(HOST_VM_RT) -classpath $(SCRATCH_DIR) GenerateFromTemplate

ifeq ($(RVM_FOR_IA32),1)
  ARCH_OPS = $(ARCH_IR_DIR)/OperatorList.dat
  ARCH_BITS = 32
endif
ifeq ($(RVM_FOR_POWERPC),1)
  ifeq ($(RVM_FOR_64_ADDR),1)
    ARCH_OPS = $(ARCH_IR_DIR)/OperatorList_Common.dat \
               $(ARCH_IR_DIR)/OperatorList_Alu64.dat \
               $(ARCH_IR_DIR)/OperatorList_Mem64.dat
    ARCH_BITS = 64              
  else 
    ARCH_OPS = $(ARCH_IR_DIR)/OperatorList_Common.dat \
               $(ARCH_IR_DIR)/OperatorList_Alu32.dat \
               $(ARCH_IR_DIR)/OperatorList_Mem32.dat
    ARCH_BITS = 32
  endif
endif


# make trickiness to allow generation of HIR/LIR without specifying an MIR.
# used in the opt compiler on JikesBT configuration
ifneq ($(ARCH_DIR),NONE)
ARCH_IR_DIR = $(RVM_ROOT)/rvm/src-generated/opt-ir/$(ARCH_DIR)/
#ARCH_OPS is defined in Make.operatorList
ARCH_IFS = $(RVM_ROOT)/rvm/src-generated/opt-ir/$(ARCH_DIR)/InstructionFormatList.dat
ARCHITECTURE = $(ARCH_DIR)
GENERATE_ARCH_OPERATOR_LIST = cat $(ARCH_OPS)
else
ARCH_OPS =
ARCH_IFS =
ARCH_IR_DIR =
ARCHITECTURE = NONE
GENERATE_ARCH_OPERATOR_LIST = echo "######\$ARCH_OPS Empty #######"
endif

RVMPACKAGE = com.ibm.jikesrvm.

$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat: $(ARCH_OPS)
	mkdir -p $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir
	$(GENERATE_ARCH_OPERATOR_LIST) > $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat

$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operator.java: OPT_Operator.template  OperatorList.dat InstructionFormatList.dat $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat $(ARCH_IFS)
	$(GENERATE) OPT_Operator.template \
	$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operator.java \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCHITECTURE=$(ARCHITECTURE) ARCH_BITS=$(ARCH_BITS) \
        ARCH_OP_LIST=$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat \
	INSTRFMTPKG=com.ibm.jikesrvm.opt.ir RVMPKG=$(RVMPACKAGE) ARCHPKG=$(ARCH_DIR)

$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operators.java: OPT_Operators.template OperatorList.dat InstructionFormatList.dat CommonOperands.dat $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat $(ARCH_IFS)
	$(GENERATE) OPT_Operators.template \
	$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_Operators.java \
	ARCH_IF_LIST=$(ARCH_IFS) \
        ARCH_OP_LIST=$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat \
	ARCHITECTURE=$(ARCHITECTURE) ARCH_BITS=$(ARCH_BITS) \
	INSTRFMTPKG=com.ibm.jikesrvm.opt.ir RVMPKG=$(RVMPACKAGE) ARCHPKG=$(ARCH_DIR)

$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_OperatorNames.java:	OPT_OperatorNames.template OperatorList.dat $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat
	$(GENERATE) OPT_OperatorNames.template \
	$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/OPT_OperatorNames.java \
        ARCH_OP_LIST=$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat \
	ARCHITECTURE=$(ARCHITECTURE) ARCH_BITS=$(ARCH_BITS) \
	INSTRFMTPKG=com.ibm.jikesrvm.opt.ir RVMPKG=$(RVMPACKAGE) ARCHPKG=$(ARCH_DIR)

$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/InstructionFormats.RAW: InstructionFormats.template OperatorList.dat InstructionFormatList.dat CommonOperands.dat $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/Arch_OperatorList.dat $(ARCH_IFS)
	$(GENERATE) InstructionFormats.template \
	$(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/InstructionFormats.RAW \
	ARCH_IR_DIR=$(ARCH_IR_DIR) \
	ARCH_IF_LIST=$(ARCH_IFS) \
	ARCHITECTURE=$(ARCHITECTURE) ARCH_BITS=$(ARCH_BITS) \
	INSTRFMTPKG=com.ibm.jikesrvm.opt.ir RVMPKG=$(RVMPACKAGE) ARCHPKG=$(ARCH_DIR)
	./splitInstructionFormats.perl $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir $(GEN_FILES_DIR)/com/ibm/jikesrvm/opt/ir/InstructionFormats.RAW
