#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author David Grove

include		$(RVM_ROOT)/rvm/build.Make.rules

MIR_LIR2MIR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/conversions/lir2mir
MIR_IR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction
JBURG_DIR  = $(SCRATCH_DIR)

all:	$(GEN_FILES_DIR)/ir.brg $(GEN_FILES_DIR)/OPT_BURS_STATE.java

$(GEN_FILES_DIR)/ir.brg: $(RVM_ROOT)/rvm/src/vm/compilers/optimizing/ir/instruction/OperatorList.dat $(MIR_LIR2MIR_DIR)/LIR2MIR.rules ir.template
	$(HOST_JAVA) -classpath `$(HOST_JAVA_PATH_ADJUST) $(SCRATCH_DIR)`\
	GenerateFromTemplate ir.template \
	`$(HOST_JAVA_PATH_ADJUST) $(GEN_FILES_DIR)/ir.brg` \
	ARCHITECTURE_IR_DIR=`$(HOST_JAVA_PATH_ADJUST) $(MIR_IR_DIR)`\
	ARCHITECTURE_LIR2MIR_DIR=`$(HOST_JAVA_PATH_ADJUST) $(MIR_LIR2MIR_DIR)`

$(GEN_FILES_DIR)/OPT_BURS_STATE.java:	$(GEN_FILES_DIR)/ir.brg burg.template
	(cd $(GEN_FILES_DIR) && $(JBURG_DIR)/jburg -p BURS ir.brg > $(ARCH_DIR).tmp)
	cat BURS_TreeNode.template $(GEN_FILES_DIR)/BURS_State.template > $(GEN_FILES_DIR)/OPT_BURS_TreeNode.java
	cat burg.template $(GEN_FILES_DIR)/$(ARCH_DIR).tmp > $(GEN_FILES_DIR)/OPT_BURS_STATE.java

