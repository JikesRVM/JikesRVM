#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_ROOT)/build.Make.rules

default: 	$(GEN_FILES_DIR)/VM_AOSOptions.java 

BOOLEAN_DAT_FILES = BooleanOptions.dat SharedBooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat SharedValueOptions.dat

$(GEN_FILES_DIR)/VM_AOSOptions.java:	VM_AOSOptions.template \
					../../compilers/optimizing/driver/MasterOptionsTemplate.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
			$(HOST_JAVA) -classpath $(SCRATCH_DIR) GenerateFromTemplate VM_AOSOptions.template \
			$(GEN_FILES_DIR)/VM_AOSOptions.java BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" VALUE_DAT_FILES="$(VALUE_DAT_FILES)" MYTYPE="VM_AOSOptions"
