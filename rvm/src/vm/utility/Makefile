#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
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
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_Options.template \
	$(GEN_FILES_DIR)/VM_Options.java \
        BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" \
	VALUE_DAT_FILES="$(VALUE_DAT_FILES)" 
