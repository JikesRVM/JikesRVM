#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author Dave Grove

include			$(RVM_ROOT)/rvm/build.Make.rules

MakeTemplates:		$(GEN_FILES_DIR)/Options.java

BOOLEAN_DAT_FILES = BooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.dat

$(GEN_FILES_DIR)/Options.java: 	Options.template \
				$(BOOLEAN_DAT_FILES) \
				$(VALUE_DAT_FILES)
	$(HOST_JAVA) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate Options.template \
	$(GEN_FILES_DIR)/Options.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" 
