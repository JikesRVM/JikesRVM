#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_ROOT)/build.Make.rules

HOST_JDK_FLAGS=
HOST_MEM_MAX=400M
HOST_MEM_MIN=300M

JCOMPILE=$(HOST_JIKES) +F +Z +KINSTRUCTION=$(TARGET_INSTRUCTION_TYPE) +E -g +U -classpath $(JAL_BUILD)/Jalapeno.classes:$(JAL_BUILD)/Jalapeno.classes/rvmrt.jar

$SRCS=	BootImage.java \
	BootImageChecker.java \
	BootImageMap.java \
	BootImageWriter2.java \
	BootImageWriterConstants.java \
	BootImageWriterMessages.java

UTIL_KLUDGE_CLASSES = java/util/HashMap* java/util/HashSet* java/util/Map*
UTIL_KLUDGE_EXTRACT = $(foreach PAT, $(UTIL_KLUDGE_CLASSES), '$(PAT)')
UTIL_KLUDGE_JAR=$(SCRATCH_DIR)/libyuck.jar

$(UTIL_KLUDGE_JAR):		$(JAL_BUILD)/Jalapeno.classes/rvmrt.jar
	cd $(SCRATCH_DIR); unzip -Uqq $< $(UTIL_KLUDGE_EXTRACT)
	cd $(SCRATCH_DIR); $(HOST_JAR) cvf $@ $(UTIL_KLUDGE_CLASSES)

#
# The -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) is a kludge to force
# the hosting jvm to use the same java.util files that the rvm will use
# at run time.  This mechanism is very fragile and only works for a 
# subset of java.util classes (discovered by trial and error).  
#

# Create a boot image.
# Parameters from 'make' command line are:
#   RVM_INSTRUCTION_TYPE = datatype used for representing machine instructions
#   RVM_BOOTIMAGE = place to put boot image
#   RVM_REPOSITORIES = path to search for classes
#   RVM_BOOTIMAGE_CLASSES = place to read names of classes to be put in boot image
#   RVM_BOOTIMAGE_COMPILER_ARGS = command line arguments to pass to boot image compiler
#   RVM_BOOTIMAGE_WRITER_ARGS = command line arguments to pass to boot image writer
#
$(RVM_BOOTIMAGE): $(SCRATCH_DIR)/BootImageWriter2.class $(UTIL_KLUDGE_JAR)
	$(HOST_JAVA) -Xbootclasspath/p:$(UTIL_KLUDGE_JAR) \
	   -Xms$(HOST_MEM_MIN) -Xmx$(HOST_MEM_MAX)\
	   -classpath `$(HOST_JAVA_PATH_ADJUST) \
			$(SCRATCH_DIR):$(RVM_REPOSITORIES)` \
	   -Djava.security.policy=rvm.security \
	   $(HOST_JDK_FLAGS) \
	   BootImageWriter2\
	   -classpath `$(HOST_JAVA_PATH_ADJUST) $(RVM_REPOSITORIES)`\
	   -n `$(HOST_JAVA_PATH_ADJUST) $(RVM_BOOTIMAGE_CLASSES)`\
	   -o `$(HOST_JAVA_PATH_ADJUST) $(RVM_BOOTIMAGE)`\
	   $(RVM_BOOTIMAGE_COMPILER_ARGS) \
	   $(RVM_BOOTIMAGE_WRITER_ARGS) \
	   $(IMAGE_ADDRESS_ARG)
	@print -n "(bootimage linked) "

$(SCRATCH_DIR)/BootImageWriter2.class:	$(SRCS)
	@$(JCOMPILE) -d $(SCRATCH_DIR) BootImageWriter2.java

