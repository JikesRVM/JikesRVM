#
# (C) Copyright IBM Corp. 2001
#
#$Id$
#
# @author Stephen Fink
# @author David Grove
# @author Michael Hind

include		$(RVM_BUILD)/Make.rules.host

default: 	$(GEN_FILES_DIR)/OPT_Options.java 

BOOLEAN_DAT_FILES = BooleanOptions.dat ../../utility/SharedBooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat ../../utility/SharedValueOptions.dat

$(GEN_FILES_DIR)/OPT_Options.java:	OPT_Options.template \
					../../utility/MasterOptions.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate OPT_Options.template \
	$(GEN_FILES_DIR)/OPT_Options.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="OPT_Options"

