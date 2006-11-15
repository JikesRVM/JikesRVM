#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001, 2006
#
# $Id: Makefile 10976 2006-11-15 15:38:26Z dgrove-oss $
#
# @author Stephen Fink
# @author Dave Grove
# @author Michael Hind
# @author Janice Shepherd

include	$(RVM_BUILD)/Make.rules.host

aos_options: $(GEN_FILES_DIR)/VM_AOSExternalOptions.java 

base_options: $(GEN_FILES_DIR)/VM_BaselineOptions.java

quick_options: $(GEN_FILES_DIR)/VM_QuickOptions.java

opt_options: $(GEN_FILES_DIR)/OPT_Options.java

vm_options: $(GEN_FILES_DIR)/VM_Options.java


BOOLEAN_DAT_FILES = 
VALUE_DAT_FILES = 

$(GEN_FILES_DIR)/VM_AOSExternalOptions.java:	VM_AOSOptions.template \
					MasterOptions.template \
					BooleanOptions.aos.dat \
					ValueOptions.aos.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_AOSOptions.template \
	$(GEN_FILES_DIR)/VM_AOSExternalOptions.java \
	BOOLEAN_DAT_FILES="BooleanOptions.aos.dat" \
	VALUE_DAT_FILES="ValueOptions.aos.dat" \
	MYTYPE="VM_AOSExternalOptions"

$(GEN_FILES_DIR)/VM_BaselineOptions.java:  VM_BaselineOptions.template \
					MasterOptions.template \
					BooleanOptions.baseline.dat SharedBooleanOptions.dat \
					ValueOptions.baseline.dat SharedValueOptions.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_BaselineOptions.template \
	$(GEN_FILES_DIR)/VM_BaselineOptions.java \
        BOOLEAN_DAT_FILES="BooleanOptions.baseline.dat SharedBooleanOptions.dat" \
	VALUE_DAT_FILES="ValueOptions.baseline.dat SharedValueOptions.dat" \
	MYTYPE="VM_BaselineOptions"

$(GEN_FILES_DIR)/VM_QuickOptions.java:  VM_QuickOptions.template \
					MasterOptions.template \
					BooleanOptions.quick.dat SharedBooleanOptions.dat \
					ValueOptions.quick.dat SharedValueOptions.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_QuickOptions.template \
	$(GEN_FILES_DIR)/VM_QuickOptions.java \
        BOOLEAN_DAT_FILES="BooleanOptions.quick.dat SharedBooleanOptions.dat" \
	VALUE_DAT_FILES="ValueOptions.quick.dat SharedValueOptions.dat" \
	MYTYPE="VM_QuickOptions"

$(GEN_FILES_DIR)/OPT_Options.java:  OPT_Options.template \
					MasterOptions.template \
					BooleanOptions.opt.dat SharedBooleanOptions.dat \
					ValueOptions.opt.dat SharedValueOptions.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate OPT_Options.template \
	$(GEN_FILES_DIR)/OPT_Options.java \
        BOOLEAN_DAT_FILES="BooleanOptions.opt.dat SharedBooleanOptions.dat" \
	VALUE_DAT_FILES="ValueOptions.opt.dat SharedValueOptions.dat" \
	MYTYPE="OPT_Options"

$(GEN_FILES_DIR)/VM_Options.java: VM_Options.template \
				BooleanOptions.vm.dat \
				ValueOptions.vm.dat
	$(TemplateExpander_HOST_VM_RT) -classpath $(SCRATCH_DIR) \
	GenerateFromTemplate VM_Options.template \
	$(GEN_FILES_DIR)/VM_Options.java \
        BOOLEAN_DAT_FILES="BooleanOptions.vm.dat" \
	VALUE_DAT_FILES="ValueOptions.vm.dat" 
