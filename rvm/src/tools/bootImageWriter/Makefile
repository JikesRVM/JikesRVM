#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author Derek Lieber

include		$(RVM_ROOT)/rvm/build.Make.rules

HOST_JDK_FLAGS=
HOST_MEM_MAX=500M
HOST_MEM_MIN=500M

LIB_JAR=$(JAL_BUILD)/RVM.classes/rvmrt.jar

ZIP_KLUDGE_CLASSES = java/util/zip/ZipConstants.class
MAP_KLUDGE_CLASSES = java/util/HashMap* java/util/HashSet* java/util/Map* java/util/AbstractMap*
TREEMAP_KLUDGE_CLASSES = java/util/TreeMap* java/util/SortedMap* java/util/Iterator* java/util/TreeSet*
LINKEDLIST_KLUDGE_CLASSES = java/util/LinkedList* java/util/List*
BUNDLE_KLUDGE_CLASSES = java/util/ResourceBundle* java/util/Locale*
UTIL_KLUDGE_CLASSES = $(ZIP_KLUDGE_CLASSES) $(MAP_KLUDGE_CLASSES) $(TREEMAP_KLUDGE_CLASSES) $(LINKEDLIST_KLUDGE_CLASSES) $(BUNDLE_KLUDGE_CLASSES)
UTIL_KLUDGE_EXTRACT = $(foreach PAT, $(UTIL_KLUDGE_CLASSES), '$(PAT)')
UTIL_KLUDGE_JAR=$(SCRATCH_DIR)/libyuck.jar

$(UTIL_KLUDGE_JAR):		$(LIB_JAR)
	cd $(SCRATCH_DIR); $(UNZIP_CMD) -Uoqq $< $(UTIL_KLUDGE_EXTRACT) > /dev/null 2> /dev/null
	cd $(SCRATCH_DIR); $(HOST_JAR) cvf $@ $(UTIL_KLUDGE_CLASSES) > /dev/null 2> /dev/null
	@print -n "(built workaround zip) "

#
# The -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) is a kludge to force
# the hosting jvm to use the same java.util files that the rvm will use
# at run time.  This mechanism is very fragile and only works for a 
# subset of java.util classes (discovered by trial and error).  
#
# Furthermore, stuff in packages like java.util may only be loaded (it 
# seems) from the boot class path.  Thus, when we are building with GNU
# Classpath, we need to put the classpath jar on the boot class path
# as well, so that the boot image writing JVM will pick up needed classes
# from that jar (i.e. classes brought in by those in libyuck) without
# getting security exceptions.  That is what command line flag 
# -Xbootclasspath/a:$(LIB_JAR) does.  ugh.
#

# Create a boot image.
# Parameters from 'make' command line are:
#   RVM_INSTRUCTION_TYPE = datatype used for representing machine instructions
#   RVM_BOOTIMAGE = place to put boot image
#   RVM_REPOSITORIES = path to search for classes
#   RVM_BOOTIMAGE_CLASSES = place to read names of classes to be put in boot image
#   RVM_BOOTIMAGE_COMPILER_ARGS = command line arguments to pass to boot image compiler
#   RVM_BOOTIMAGE_WRITER_ARGS = command line arguments to pass to boot image writer
#   VERBOSE_ARG = command-line argument for verbose host jdk options
$(RVM_BOOTIMAGE): $(UTIL_KLUDGE_JAR)
	$(HOST_JAVA) -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) \
	    -Xbootclasspath/a:$(LIB_JAR) \
	   -Xms$(HOST_MEM_MIN) -Xmx$(HOST_MEM_MAX)\
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
	@print -n "(bootimage linked) "

