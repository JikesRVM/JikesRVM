#
# (C) Copyright IBM Corp. 2002
#
#$Id$
#
# @author David Grove

COMMON_DIR = $(RVM_ROOT)/rvm/src/vm/compilers/optimizing/ir/conversions/lir2mir
ifeq ($(RVM_FOR_64_ADDR),1)
 RULE_FILES = 	$(MIR_LIR2MIR_DIR)/PPC_Common.rules \
		$(MIR_LIR2MIR_DIR)/PPC_Alu64.rules \
		$(MIR_LIR2MIR_DIR)/PPC_Mem64.rules 
else 
  ifeq ($(RVM_FOR_32_ADDR),1)
RULE_FILES = 	$(MIR_LIR2MIR_DIR)/PPC_Common.rules \
		$(MIR_LIR2MIR_DIR)/PPC_Alu32.rules \
		$(MIR_LIR2MIR_DIR)/PPC_Mem32.rules 
  endif
endif



