#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author Janice Shepherd

# currently the baseline compiler only has shared options - options shared with optimizing compiler


include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/VM_BASEOptions.java
			@print -n "(baseline templates expanded) "

BOOLEAN_DAT_FILES = SharedBooleanOptions.dat
VALUE_DAT_FILES = SharedValueOptions.dat

$(GEN_FILES_DIR)/VM_BASEOptions.java:	VM_BASEOptions.template \
					../optimizing/driver/MasterOptionsTemplate.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(HOST_JAVA) -classpath `$(HOST_JAVA_PATH_ADJUST) $(SCRATCH_DIR)` \
	GenerateFromTemplate VM_BASEOptions.template \
	`$(HOST_JAVA_PATH_ADJUST) $(GEN_FILES_DIR)/VM_BASEOptions.java` \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="VM_BASEOptions"


