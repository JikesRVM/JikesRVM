#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author Igor Pechtchanski

include		$(RVM_BUILD)/Make.rules.host

MIR_IR_DIR = $(RVM_ROOT)/rvm/src/vm/arch/$(ARCH_DIR)/compilers/optimizing/ir/instruction

default:  $(GEN_FILES_DIR)/OPT_OperatorClass.java

$(GEN_FILES_DIR)/OPT_OperatorClass.java:	OPT_OperatorClass.template $(MIR_IR_DIR)/ResourceList.dat $(MIR_IR_DIR)/OperatorClassList.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate OPT_OperatorClass.template  \
	$(GEN_FILES_DIR)/OPT_OperatorClass.java \
	ARCHITECTURE_IR_DIR=$(MIR_IR_DIR)

