#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_ROOT)/rvm/build.Make.rules

default: 	$(GEN_FILES_DIR)/OPT_Options.java 

ifeq ($(IS_ADAPTIVE),1)
BOOLEAN_DAT_FILES = BooleanOptions.dat ../../../adaptive/utility/SharedBooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat ../../../adaptive/utility/SharedValueOptions.dat
else
BOOLEAN_DAT_FILES = BooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat
endif


$(GEN_FILES_DIR)/OPT_Options.java:	OPT_Options.template \
					MasterOptionsTemplate.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(HOST_JAVA) -classpath `$(HOST_JAVA_PATH_ADJUST) $(SCRATCH_DIR)` \
	GenerateFromTemplate OPT_Options.template \
	`$(HOST_JAVA_PATH_ADJUST) $(GEN_FILES_DIR)/OPT_Options.java` \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="OPT_Options"

