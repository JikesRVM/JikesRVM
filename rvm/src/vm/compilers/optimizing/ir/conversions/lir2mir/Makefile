#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author David Grove

include		$(RVM_BUILD)/Make.rules.host

MIR_LIR2MIR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/conversions/lir2mir
MIR_IR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction
JBURG_DIR  = $(SCRATCH_DIR)

# define RULE_FILES
include $(MIR_LIR2MIR_DIR)/Makefile

all:	$(GEN_FILES_DIR)/OPT_BURS_STATE.java

$(GEN_FILES_DIR)/LIR2MIR.rules:	$(RULE_FILES)
	cat $(RULE_FILES) > $(GEN_FILES_DIR)/LIR2MIR.rules

$(GEN_FILES_DIR)/ir.brg: $(RVM_ROOT)/rvm/src/vm/compilers/optimizing/ir/instruction/OperatorList.dat ir.template $(GEN_FILES_DIR)/LIR2MIR.rules
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR)\
	GenerateFromTemplate ir.template \
	$(GEN_FILES_DIR)/ir.brg \
	ARCHITECTURE_IR_DIR=$(MIR_IR_DIR) \
        ARCH_OP_LIST=$(GEN_FILES_DIR)/Arch_OperatorList.dat \
	THE_RULE_FILE=$(GEN_FILES_DIR)/LIR2MIR.rules

$(GEN_FILES_DIR)/OPT_BURS_STATE.java:	$(GEN_FILES_DIR)/ir.brg burg.template
	(cd $(GEN_FILES_DIR) && $(JBURG_DIR)/jburg -p BURS ir.brg > $(ARCH_DIR).tmp)
	cat OPT_BURS_TreeNode.template $(GEN_FILES_DIR)/BURS_State.template > $(GEN_FILES_DIR)/OPT_BURS_TreeNode.java
	cat burg.template $(GEN_FILES_DIR)/$(ARCH_DIR).tmp > $(GEN_FILES_DIR)/OPT_BURS_STATE.java

