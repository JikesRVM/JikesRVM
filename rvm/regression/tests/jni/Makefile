#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Dick Attanasio
# @author Julian Dolby
# @author Dave Grove
#
# to run all the tests 'make' or 'make sanity'
# to run a single test 'make single ONE_TEST=<test_name>'
#
include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/jni
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=64
START_NAME=
EXPECTED=
BENCH_JDK_ARGS="-Djava.library.path=$(BENCH_CLASS_CP)"
BENCH_RVM_ARGS="-Djava.library.path=$(BENCH_CLASS_CP)"
CFLAGS+=-Wno-unused -Wno-conversion		#for GCC
QUIET = -quiet

include 	$(JAL_ROOT)/rvm/regression/Make.rules

## TODO: We need to add SelectTest, as soon as it works with 
## RVM_FOR_SINGLE_VIRTUAL_PROCESSOR.

# Tests for JNI 1.1
TESTS_11 := Allocation ArgumentPassing ArrayFunctions ClassQuery CriticalCopy \
        FieldAccess MethodInvocation MonitorTest NativeException \
	NullIdentity StringFunctions TestGC t3GT3

# Tests for JNI 1.2
TESTS_12 := JNI12 StringRegion

TESTS := $(TESTS_11) $(TESTS_12)

LIBS = $(TESTS:%=lib%.a) $(TESTS:%=lib%.so) $(TESTS:%=lib%.jnilib) 

sanity: $(LIBS) $(TESTS)
	@$(MAKE) START_NAME="jni" do-sanity-finish

check:
	@$(FGREP) -q "PASS:" < $(OUT) > /dev/null
	@$(FGREP) -v "FAIL:" < $(OUT)


ONE_TEST_LIB = $(ONE_TEST:%=lib%.a) $(ONE_TEST:%=lib%.so) $(ONE_TEST:%=lib%.jnilib)

single:	$(ONE_TEST_LIB)
	$(MAKE) START_NAME=$(ONE_TEST) START_ARGS=$(QUIET) do-sanity-compare
	$(MAKE) do-sanity-finish

vpath %.c	$(BENCH_HOME)

%.h:		%.class
	$(HOST_JAVA_HOME)/bin/javah -classpath $(BENCH_CLASS_CP) -jni -o $(BENCH_CLASS_CP)/$@ $(basename $(notdir $<))

lib%.a:	%.c %.h
	$(CC) $(CFLAGS) -c -o $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include $<
	$(LDSHARED) $(CXXFLAGS) -o $(BENCH_CLASS_CP)/$@ -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o

%.so:	%.a
	cp -f $(BENCH_CLASS_CP)/$< $(BENCH_CLASS_CP)/$@

%.jnilib:	%.a
	cp -f $(BENCH_CLASS_CP)/$< $(BENCH_CLASS_CP)/$@

$(TESTS): % :
	$(MAKE) START_NAME=$@ START_ARGS=$(QUIET) do-sanity-check-rule


