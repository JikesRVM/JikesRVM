#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author Janice Shepherd

include	$(RVM_BUILD)/Make.rules.host

MakeTemplates:		$(GEN_FILES_DIR)/VM_BaselineOptions.java

BOOLEAN_DAT_FILES = BooleanOptions.baseline.dat $(RVM_ROOT)/rvm/src/vm/compilers/utility/SharedBooleanOptions.dat
VALUE_DAT_FILES = ValueOptions.baseline.dat $(RVM_ROOT)/rvm/src/vm/compilers/utility/SharedValueOptions.dat

$(GEN_FILES_DIR)/VM_BaselineOptions.java: VM_BaselineOptions.template \
					$(RVM_ROOT)/rvm/src/vm/compilers/utility/MasterOptions.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_BaselineOptions.template \
	$(GEN_FILES_DIR)/VM_BaselineOptions.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" \
	MYTYPE="VM_BaselineOptions"
