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
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/jni
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=64
START_NAME=
EXPECTED=
BENCH_JDK_ARGS=-Djava.libaray.path=$(BENCH_CLASS_CP)
BENCH_RVM_ARGS=-Djava.libaray.path=$(BENCH_CLASS_CP)

include 	$(JAL_ROOT)/rvm/regression/Make.rules

TESTS = Allocation ArgumentPassing ClassQuery CriticalCopy \
        FieldAccess MethodInvocation MonitorTest StringFunctions \
	TestGC t3GT3

# TESTS that currently fail for one reason or another
#       TEST			REASON FOR EXCLUSION
# 	ArrayFunctions 		JDK claims that a native function attempts to unpin and object that is not pinned
#       NativeException 	cause JDK to dump core.
#       StackResize 		fails on AIX RVM

LIBS = $(TESTS:%=lib%.a) $(TESTS:%=lib%.so) 

sanity: clean $(LIBS) $(TESTS)
	$(MAKE) do-sanity-finish

ONE_TEST_LIB = $(ONE_TEST:%=lib%.a) $(ONE_TEST:%=lib%.so)

single:	clean $(ONE_TEST_LIB)
	export LIBPATH=$(BENCH_CLASS_CP); $(MAKE) START_NAME=$(ONE_TEST) START_ARGS=-quiet do-sanity-compare
	$(MAKE) do-sanity-finish

vpath %.c	$(BENCH_HOME)

%.h:		%.class
	$(HOST_JAVA_HOME)/bin/javah -classpath $(BENCH_CLASS_CP) -jni -o $(BENCH_CLASS_CP)/$@ $(basename $(notdir $<))

lib%.a:	%.c %.h
	$(CXX) -c -o $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include -O -g $<
	$(LDSHARED) -o $(BENCH_CLASS_CP)/$@ -I$(BENCH_CLASS_CP) -I$(JAL_ROOT)/rvm/src/include -O -g $(BENCH_CLASS_CP)/$(basename $(notdir $<)).o

%.so:	%.a
	cp -f $(BENCH_CLASS_CP)/$< $(BENCH_CLASS_CP)/$@

$(TESTS): % :
	export LIBPATH=$(BENCH_CLASS_CP); $(MAKE) START_NAME=$@ START_ARGS=-quiet do-sanity-compare

