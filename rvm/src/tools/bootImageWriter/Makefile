# -*-makefile-*-
# (C) Copyright © IBM Corp. 2001, 2004, 2005
#
#$Id$
#

# Boot Image Writer Makefile.  
#
# Create a boot image.
# Parameters from 'make' command line are:
#   RVM_BOOTIMAGE = place to put boot image
#   RVM_REPOSITORIES = path to search for classes
#   RVM_BOOTIMAGE_CLASSES = place to read names of classes to be put in boot image
#   RVM_BOOTIMAGE_COMPILER_ARGS = command line arguments to pass to boot image compiler
#   RVM_BOOTIMAGE_WRITER_ARGS = command line arguments to pass to boot image writer
#   VERBOSE_ARG = command-line argument for verbose host jdk options
#   STUBS_DIR = directory where we'll built the utility_kludge.jar.
#
# @author Derek Lieber
# @modified Steven Augart   March, 2004, to handle using alternative VMs for booting.

include		$(RVM_BUILD)/Make.rules.host
SHELL=${BASH}			# Set shell to bash because we use 'echo -n' 
			        # below, and /bin/sh on AIX does not support
                                # 'echo -n'.

ifeq (${HOST_VM_TYPE},IBM-50)
    HOST_JDK_FLAGS=-Xss1m
else 
    HOST_JDK_FLAGS=
endif
HOST_MEM_MAX=500M

LIB_JAR=$(JAL_BUILD)/RVM.classes/rvmrt.jar

# The below applies to when we're using a different donor JVM to do our 
# boot image writing.  It describes a mechanism that we set up to work 
# with the Sun HotSpot JVM (as packaged by the Blackdown organization).
#
# Classes in packages like java.util may only be loaded (it 
# seems) from the boot class path.  Thus, when we are building with GNU
# Classpath, we need to put the GNU Classpath jar on the boot class path
# as well, so that the boot image writing JVM will pick up needed classes
# from that jar without getting security exceptions.  
# That is what command line flag -Xbootclasspath/a:$(LIB_JAR) does.


##  host_vm_envars = environment variables we set for the HOST_VM_RT program.
##  host_vm_bootclasspath_args = args to set the host VM's bootclasspath, if any

## We have to reset the environment in order to 

ifeq (${HOST_VM_TYPE},Kaffe)
   phony_home_dir := $(SCRATCH_DIR)
   host_vm_envars := BOOTCLASSPATH_SUFFIX=$(RVM_REPOSITORIES) \
		     HOME=$(phony_home_dir)
endif


ifeq (${HOST_VM_TYPE},Sun)
   host_vm_bootclasspath_args := -Xbootclasspath/a:$(RVM_REPOSITORIES)
endif

ifeq (${HOST_VM_TYPE},IBM-50)
   host_vm_bootclasspath_args := -Xbootclasspath/a:$(RVM_REPOSITORIES)
endif

ifeq (${HOST_VM_TYPE},gij)
   host_mem_size_arg := 	   --mx=$(HOST_MEM_MAX)
else
   host_mem_size_arg := 	   -Xmx$(HOST_MEM_MAX)
endif

bootimage: 
ifeq (${HOST_VM_TYPE},Kaffe)
	$(RM) $(phony_home_dir)/.kafferc
	$(CP) dot-kafferc $(phony_home_dir)/.kafferc
endif
	$(host_vm_envars) \
	$(HOST_VM_RT) \
	   $(host_vm_bootclasspath_args) \
		$(host_mem_size_arg) \
	   -classpath $(STUBS_DIR):$(SCRATCH_DIR):$(RVM_REPOSITORIES) \
	   -Djava.security.policy=rvm.security \
	   $(HOST_JDK_FLAGS) $(VERBOSE_ARG) \
	   BootImageWriter\
	   -classpath $(RVM_REPOSITORIES) \
	   -n $(RVM_BOOTIMAGE_CLASSES) \
	   -oc $(RVM_BOOTIMAGE_CODE) \
	   -od $(RVM_BOOTIMAGE_DATA) \
	   $(RVM_BOOTIMAGE_COMPILER_ARGS) \
	   $(RVM_BOOTIMAGE_WRITER_ARGS) \
	   $(IMAGE_ADDRESS_ARG)
	@echo -n "(bootimage linked) "

