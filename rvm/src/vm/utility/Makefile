#
# (C) Copyright IBM Corp. 2003
#
#$Id$
# @author Dave Grove

include			$(RVM_BUILD)/Make.rules.host

MakeTemplates:		$(GEN_FILES_DIR)/VM_Options.java

BOOLEAN_DAT_FILES = BooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat

$(GEN_FILES_DIR)/VM_Options.java: VM_Options.template \
				$(BOOLEAN_DAT_FILES) \
				$(VALUE_DAT_FILES)
	$(HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_Options.template \
	$(GEN_FILES_DIR)/VM_Options.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" 
