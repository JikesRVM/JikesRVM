# -*-makefile-*-
# (C) Copyright © IBM Corp. 2001, 2004
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
#
# @author Derek Lieber
# @modified Steven Augart   March, 2004, to handle using alternative VMs for booting.

include		$(RVM_BUILD)/Make.rules.host
SHELL=${BASH}			# Set shell to bash because we use 'echo -n' 
			        # below, and /bin/sh on AIX does not support
                                # 'echo -n'.

HOST_JDK_FLAGS=
HOST_MEM_MAX=400M

LIB_JAR=$(JAL_BUILD)/RVM.classes/rvmrt.jar

ZIP_KLUDGE_CLASSES = java/util/zip/ZipConstants.class
MAP_KLUDGE_CLASSES = java/util/HashMap* java/util/HashSet* java/util/Map* java/util/AbstractMap*
TREEMAP_KLUDGE_CLASSES = java/util/TreeMap* java/util/SortedMap* java/util/Iterator* java/util/TreeSet*
LINKEDLIST_KLUDGE_CLASSES = java/util/LinkedList* java/util/List*
BUNDLE_KLUDGE_CLASSES = java/util/ResourceBundle* java/util/Locale*
UTIL_KLUDGE_CLASSES = $(ZIP_KLUDGE_CLASSES) $(MAP_KLUDGE_CLASSES) $(TREEMAP_KLUDGE_CLASSES) $(LINKEDLIST_KLUDGE_CLASSES) $(BUNDLE_KLUDGE_CLASSES)
UTIL_KLUDGE_EXTRACT = $(foreach PAT, $(UTIL_KLUDGE_CLASSES), '$(PAT)')
UTIL_KLUDGE_JAR=$(SCRATCH_DIR)/utility_kludge.jar

$(UTIL_KLUDGE_JAR):		$(LIB_JAR)
# args to UNZIP_CMD:
# -o: Overwrite if we need to
# -u: Update
	cd $(SCRATCH_DIR); $(UNZIP_CMD) -uoqq $< $(UTIL_KLUDGE_EXTRACT)
	cd $(SCRATCH_DIR); $(JAR) -cf $@ $(UTIL_KLUDGE_CLASSES)
	@echo -n "(built $$(basename ${UTIL_KLUDGE_JAR})) "

# The below applies to when we're using a different donor JVM to do our 
# boot image writing.  It describes a mechanism that we set up to work with the 
# Sun HotSpot JVM (as packaged by the Blackdown organization).
#
# The -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) is a kludge to force
# the hosting jvm to use the same java.util files that the rvm will use
# at run time.  This mechanism is very fragile and only works for a 
# subset of java.util classes (discovered by trial and error).  
#
# Furthermore, stuff in packages like java.util may only be loaded (it 
# seems) from the boot class path.  Thus, when we are building with GNU
# Classpath, we need to put the GNU Classpath jar on the boot class path
# as well, so that the boot image writing JVM will pick up needed classes
# from that jar (i.e. classes brought in by those in UTIL_KLUDGE_JAR) without
# getting security exceptions.  That is what command line flag 
# -Xbootclasspath/a:$(LIB_JAR) does.  ugh.


##  host_vm_envars = environment variables we set for the HOST_VM_RT program.
##  host_vm_bootclasspath_args = args to set the host VM's bootclasspath, if any

## We have to reset the environment in order to 

ifeq (${HOST_VM_TYPE},Kaffe)
   phony_home_dir := $(SCRATCH_DIR)
   host_vm_envars := BOOTCLASSPATH_PREFIX=$(UTIL_KLUDGE_JAR) \
		     BOOTCLASSPATH_SUFFIX=$(RVM_REPOSITORIES) \
		     HOME=$(phony_home_dir)
endif


ifeq (${HOST_VM_TYPE},Sun)
   host_vm_bootclasspath_args := -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) \
				 -Xbootclasspath/a:$(LIB_JAR)
endif

$(RVM_BOOTIMAGE): $(UTIL_KLUDGE_JAR)
ifeq (${HOST_VM_TYPE},Kaffe)
	$(RM) $(phony_home_dir)/.kafferc
	$(CP) dot-kafferc $(phony_home_dir)/.kafferc
endif
	$(host_vm_envars) \
	$(HOST_VM_RT) \
	   $(host_vm_bootclasspath_args) \
	   -Xmx$(HOST_MEM_MAX) \
	   -classpath $(SCRATCH_DIR):$(RVM_REPOSITORIES) \
	   -Djava.security.policy=rvm.security \
	   $(HOST_JDK_FLAGS) $(VERBOSE_ARG) \
	   BootImageWriter\
	   -classpath $(RVM_REPOSITORIES) \
	   -n $(RVM_BOOTIMAGE_CLASSES) \
	   -o $(RVM_BOOTIMAGE) \
	   $(RVM_BOOTIMAGE_COMPILER_ARGS) \
	   $(RVM_BOOTIMAGE_WRITER_ARGS) \
	   $(IMAGE_ADDRESS_ARG)
	@echo -n "(bootimage linked) "

