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

HOST_JDK_FLAGS=
HOST_MEM_MAX=400M

LIB_JAR=$(JAL_BUILD)/RVM.classes/rvmrt.jar

ZIP_KLUDGE_CLASSES = java/util/zip/ZipConstants.class
MAP_KLUDGE_CLASSES = java/util/HashMap* java/util/HashSet* java/util/Map* java/util/AbstractMap*
TREEMAP_KLUDGE_CLASSES = java/util/TreeMap* java/util/SortedMap* java/util/Iterator* java/util/TreeSet*
LINKEDLIST_KLUDGE_CLASSES = java/util/LinkedList* java/util/List* java/util/AbstractCollection* java/util/AbstractList*
BUNDLE_KLUDGE_CLASSES = java/util/ResourceBundle* java/util/Locale*
## the SystemProperties.class stub is only needed for Classpath
## versions 0.13 and later, but it does no harm to include it for 
## earlier versions.
UTIL_KLUDGE_STUB_CLASSNAMES = gnu/classpath/SystemProperties.class
UTIL_KLUDGE_EXTRACT_PATTERNS = $(ZIP_KLUDGE_CLASSES) $(MAP_KLUDGE_CLASSES) $(TREEMAP_KLUDGE_CLASSES) $(LINKEDLIST_KLUDGE_CLASSES) $(BUNDLE_KLUDGE_CLASSES) 
UTIL_KLUDGE_EXTRACT_PATTERNS_QUOTED = $(foreach PAT, $(UTIL_KLUDGE_EXTRACT_PATTERNS), '$(PAT)')

UTIL_KLUDGE_JAR=$(STUBS_DIR)/utility_kludge.jar
STUBS_SRC_DIR := $(JAL_ROOT)/rvm/src/tools/bootImageWriter/utility_kludge_stubs
UTIL_KLUDGE_STUB_CLASS_FILES := $(foreach name, $(UTIL_KLUDGE_STUB_CLASSNAMES), $(STUBS_DIR)/${name})
UTIL_KLUDGE_STUB_SOURCE_FILES := $(foreach name, $(UTIL_KLUDGE_STUB_CLASSNAMES), $(STUBS_SRC_DIR)/$(name:.class=.java))
UTIL_KLUDGE_CLASSES = $(UTIL_KLUDGE_EXTRACT_PATTERNS) $(UTIL_KLUDGE_STUB_CLASSNAMES)

$(UTIL_KLUDGE_JAR):		$(LIB_JAR) $(UTIL_KLUDGE_STUB_CLASS_FILES)
# args to UNZIP_CMD:
# -o: Overwrite if we need to
# -u: Update
	cd $(STUBS_DIR); $(UNZIP_CMD) -uoqq $< $(UTIL_KLUDGE_EXTRACT_PATTERNS_QUOTED)
	cd $(STUBS_DIR); $(JAR) -cf $@ $(UTIL_KLUDGE_CLASSES) 
	@echo -n "(built $$(basename ${UTIL_KLUDGE_JAR})) "

$(UTIL_KLUDGE_STUB_CLASS_FILES): $(UTIL_KLUDGE_STUB_SOURCE_FILES)
	$(HOST_JAVAC) -d $(STUBS_DIR) $^


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
				 -Xbootclasspath/a:$(RVM_REPOSITORIES)
endif

ifeq (${HOST_VM_TYPE},gij)
   host_mem_size_arg := 	   --mx=$(HOST_MEM_MAX)
else
   host_mem_size_arg := 	   -Xmx$(HOST_MEM_MAX)
endif

$(RVM_BOOTIMAGE): $(UTIL_KLUDGE_JAR)
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
	   -o $(RVM_BOOTIMAGE) \
	   $(RVM_BOOTIMAGE_COMPILER_ARGS) \
	   $(RVM_BOOTIMAGE_WRITER_ARGS) \
	   $(IMAGE_ADDRESS_ARG)
	@echo -n "(bootimage linked) "

