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

include 	$(JAL_ROOT)/rvm/regression/Make.rules

TESTS = Allocation ArgumentPassing ArrayFunctions ClassQuery CriticalCopy \
        FieldAccess MethodInvocation MonitorTest NativeException \
	StringFunctions TestGC t3GT3

LIBS = $(TESTS:%=lib%.a) $(TESTS:%=lib%.so) 

sanity: $(LIBS) $(TESTS)
	@$(MAKE) START_NAME="jni" do-sanity-finish

check:
	@$(FGREP) -q "PASS:" < $(OUT) > /dev/null
	@$(FGREP) -v "FAIL:" < $(OUT)


ONE_TEST_LIB = $(ONE_TEST:%=lib%.a) $(ONE_TEST:%=lib%.so)

single:	$(ONE_TEST_LIB)
	$(MAKE) START_NAME=$(ONE_TEST) START_ARGS=-quiet do-sanity-compare
	$(MAKE) do-sanity-finish

vpath %.c	$(BENCH_HOME)

%.h:		%.class
	$(HOST_JAVA_HOME)/bin/javah -classpath $(BENCH_CLASS_CP) -jni -o $(BENCH_CLASS_CP)/$@ $(basename $(notdir $<))

lib%.a:	%.c %.h
	$(CC) $(CFLAGS) -c -o $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include $<
	$(LDSHARED) $(CXXFLAGS) -o $(BENCH_CLASS_CP)/$@ -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o

%.so:	%.a
	cp -f $(BENCH_CLASS_CP)/$< $(BENCH_CLASS_CP)/$@

$(TESTS): % :
	$(MAKE) START_NAME=$@ START_ARGS=-quiet do-sanity-check-rule


