#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author Janice Shepherd

include	$(RVM_BUILD)/Make.rules.host

MakeTemplates:		$(GEN_FILES_DIR)/VM_QuickOptions.java

BOOLEAN_DAT_FILES = BooleanOptions.dat $(RVM_ROOT)/rvm/src/vm/compilers/utility/SharedBooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat $(RVM_ROOT)/rvm/src/vm/compilers/utility/SharedValueOptions.dat

$(GEN_FILES_DIR)/VM_QuickOptions.java: VM_QuickOptions.template \
					$(RVM_ROOT)/rvm/src/vm/compilers/utility/MasterOptions.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_QuickOptions.template \
	$(GEN_FILES_DIR)/VM_QuickOptions.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="VM_QuickOptions"
