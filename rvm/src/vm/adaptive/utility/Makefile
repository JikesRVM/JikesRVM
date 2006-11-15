#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# @author: Michael Hind

include		$(RVM_BUILD)/Make.rules.host

default: 	$(GEN_FILES_DIR)/VM_AOSExternalOptions.java 

BOOLEAN_DAT_FILES = BooleanOptions.aos.dat
VALUE_DAT_FILES = ValueOptions.aos.dat

$(GEN_FILES_DIR)/VM_AOSExternalOptions.java:	VM_AOSOptions.template \
					../../compilers/utility/MasterOptions.template \
					$(BOOLEAN_DAT_FILES) \
					$(VALUE_DAT_FILES)
			$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) GenerateFromTemplate VM_AOSOptions.template \
			$(GEN_FILES_DIR)/VM_AOSExternalOptions.java BOOLEAN_DAT_FILES="$(BOOLEAN_DAT_FILES)" VALUE_DAT_FILES="$(VALUE_DAT_FILES)" MYTYPE="VM_AOSExternalOptions"
