#
# (C) Copyright IBM Corp. 2001
#$Id$
#
# This Makefile is to be called from the jdp script file where the appropriate
# environment variables are set for the platform
#
# @author Ton Ngo

include $(RVM_ROOT)/rvm/Make.rules

JCOMPILE=$(JCOMPILE_RVM)

# all compiled codes go here
TOOLS_DIR:=$(RVM_BUILD)/RVM.tools

CLASSPATH:=$(RVM_BUILD)/RVM.scratch:$(RVM_BUILD)/RVM.classes:$(RVM_BUILD)/RVM.classes/rvmrt.jar

.SUFFIXES:	.java .class

.java.class:	
	$(JCOMPILE) -g -classpath $(CLASSPATH) $< 

CLASSES   = \
	jdp.class \
	Debugger.class \
	CommandLine.class \
	breakpoint.class \
	BootMap.class \
	BootMapExternal.class \
	BC.class \
	SourceSnapshot.class \
	SourceFinder.class \
	jdpConstants.class \
	jdpMacro.class \
	OsProcess.class \
	memory.class \
	register.class \
	OsProcessExternal.class \
	memoryExternal.class \
	registerExternal.class \
	registerConstants.class \
	BcPrologException.class \
	BcNativeException.class \
	LnNotAvailException.class \
	BmapMultipleException.class \
	BmapNotFoundException.class \
	memoryException.class \
	CommandCompletion.class \
	dictionaryExtension.class \
	breakpointList.class \
	RemoteInterpreter.class \
	ByteCodeContext.class \
	InterpreterBase.class \
	InterpreterStack.class \
	addressRemapper.class \
	unInitType.class \
	mapVM.class 

# Class files that contain native methods, and for which there is a
# corresponding .c file.  If this makefile is to be used for .class files only,
# then NATIVECLASSES can be omitted.

NATIVECLASSES_POWERPC = \
	Platform.class 

NATIVECLASSES_INTEL = \
	Platform.class \
	IntelDisassembler.class


# The .o files associated with any extra .c or .C modules to be included
# in the build.  Note:  These MUST be given here as .o files.

EXTRA_C_MODULES = OsProcessExternal.o \
	memoryExternal.o \
	registerExternal.o 

# -----------------------------------------------------------------------------

.SUFFIXES: .java .class .h .o .c .C .u

# This bit handles automatic header file dependencies.
#	@touch $(NATIVECLASSES:.class=.u) $(EXTRA_C_MODULES:.o=.u)
#	@(cat $(NATIVECLASSES:.class=.u) $(EXTRA_C_MODULES:.o=.u);\
#             echo "include Makefile") > Makefile.tmp
#	@$(MAKE) -f Makefile.tmp all_1

copy_intel: 
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/Makefile $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/intel/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/intel/*.c $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/interpreter/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/vm/arch/intel/disassembler/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/vm/arch/intel/disassembler/*.C $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/vm/arch/intel/disassembler/*.c $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/vm/arch/intel/disassembler/*.h $(TOOLS_DIR)/.
# mapVM needs preprocessing for arch-dependency and was placed in scratch dir
	@rm -f $(TOOLS_DIR)/mapVM.java
	@cp -f $(RVM_BUILD)/RVM.scratch/mapVM.java $(TOOLS_DIR)/mapVM.java

copy_powerPC:
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/Makefile $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/powerPC/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/powerPC/*.c $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/tools/jdp/interpreter/*.java $(TOOLS_DIR)/.
	@cp -f $(RVM_ROOT)/rvm/src/vm/arch/powerPC/disassembler/*.java $(TOOLS_DIR)/.
# mapVM needs preprocessing for arch-dependency and was placed in scratch dir
	@rm -f $(TOOLS_DIR)/mapVM.java
	@cp -f $(RVM_BUILD)/RVM.scratch/mapVM.java $(TOOLS_DIR)/mapVM.java


compile:  $(CLASSES) $(NATIVECLASSES) lib_$(ARCH)



lib_powerPC: $(NATIVECLASSES_POWERPC:.class=.h) $(NATIVECLASSES_POWERPC:.class=.o) \
            $(EXTRA_C_MODULES) 
	grep " JNICALL " $(NATIVECLASSES_POWERPC:.class=.h) | sed "s/.* JNICALL //g">$@.exp
	$(JNI_NATIVE_LIBS_LD)$@.exp \
           -o $(TOOLS_DIR)/libosprocess.a $(NATIVECLASSES_POWERPC:.class=.o) $(EXTRA_C_MODULES) 


lib_intel: $(NATIVECLASSES_INTEL:.class=.h) $(NATIVECLASSES_INTEL:.class=.o) \
            $(EXTRA_C_MODULES) 	disasm.o ihnpdsm.o 
	gcc -shared -L/usr/lib -L/lib \
           -o $(TOOLS_DIR)/libosprocess.so $(NATIVECLASSES_INTEL:.class=.o) $(EXTRA_C_MODULES)	disasm.o ihnpdsm.o   

#Note: must have the option -funsigned-bitfields for the disassembler to work
.c.o:
	@mkdir -p $(@D)
	gcc -funsigned-bitfields -c -I$(HOST_JAVA_HOME)/include -I$(RVM_BUILD)/RVM.scratch -o $@ $< 

.C.o:
	gcc -funsigned-bitfields -c -I$(HOST_JAVA_HOME)/include -o $@ $< 

.class.h:
	$(HOST_JAVA_HOME)/bin/javah -jni -classpath .:$(CLASSPATH) -o $(TOOLS_DIR)/$*.h $*

clean:
	(cd $(TOOLS_DIR); rm -f *)


