#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author Janice Shepherd

# currently the baseline compiler only has shared options - options shared with optimizing compiler


include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/VM_BaseOptions.java
			@print -n "(baseline templates expanded) "

BOOLEAN_DAT_FILES = SharedBooleanOptions.dat BooleanOptions.dat
VALUE_DAT_FILES = SharedValueOptions.dat ValueOptions.dat

$(GEN_FILES_DIR)/VM_BaseOptions.java:	VM_BaseOptions.template \
					../optimizing/driver/MasterOptionsTemplate.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(HOST_JAVA) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_BaseOptions.template \
	$(GEN_FILES_DIR)/VM_BaseOptions.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="VM_BaseOptions"


