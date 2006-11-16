#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author David Grove

include		$(RVM_BUILD)/Make.rules.host

MIR_IR_DIR = $(RVM_ROOT)/rvm/src-generated/opt-ir/$(ARCH_DIR)/
JBURG_DIR  = $(SCRATCH_DIR)

BURG_TEMPLATE=$(RVM_ROOT)/tools-external/jburg/burg.template

# Pick the input rules file depending on the target architecture
ifeq ($(RVM_FOR_IA32),1)
  RULE_FILES = src-ia32/IA32.rules 
endif

ifeq ($(RVM_FOR_POWERPC),1)
 ifeq ($(RVM_FOR_64_ADDR),1)
   RULE_FILES =	src-ppc/PPC_Common.rules \
		src-ppc/PPC_Alu64.rules \
		src-ppc/PPC_Mem64.rules 
 else 
   RULE_FILES =	src-ppc/PPC_Common.rules \
		src-ppc/PPC_Alu32.rules \
		src-ppc/PPC_Mem32.rules 
 endif
endif

all:	$(GEN_FILES_DIR)/OPT_BURS_STATE.java

$(GEN_FILES_DIR)/LIR2MIR.rules:	$(RULE_FILES)
	cat $(RULE_FILES) > $(GEN_FILES_DIR)/LIR2MIR.rules

$(GEN_FILES_DIR)/ir.brg: $(RVM_ROOT)/rvm/src-generated/opt-ir/OperatorList.dat ir.template $(GEN_FILES_DIR)/LIR2MIR.rules
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate ir.template \
	$(GEN_FILES_DIR)/ir.brg \
	ARCHITECTURE_IR_DIR=$(MIR_IR_DIR) \
        ARCH_OP_LIST=$(GEN_FILES_DIR)/Arch_OperatorList.dat \
	THE_RULE_FILE=$(GEN_FILES_DIR)/LIR2MIR.rules

$(GEN_FILES_DIR)/OPT_BURS_STATE.java:	$(GEN_FILES_DIR)/ir.brg $(BURG_TEMPLATE)
	(cd $(GEN_FILES_DIR) && $(JBURG_DIR)/jburg -p BURS ir.brg > $(ARCH_DIR).tmp)
	cat OPT_BURS_TreeNode.template $(GEN_FILES_DIR)/BURS_State.template > $(GEN_FILES_DIR)/OPT_BURS_TreeNode.java
	cat $(BURG_TEMPLATE) $(GEN_FILES_DIR)/$(ARCH_DIR).tmp > $(GEN_FILES_DIR)/OPT_BURS_STATE.java

