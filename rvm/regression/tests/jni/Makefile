#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/jni
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=64
START_NAME=
EXPECTED=

include 	$(JAL_ROOT)/rvm/regression/Make.rules

vpath %.c	$(BENCH_HOME)

%.h:		%.class
	$(HOST_JAVA_HOME)/bin/javah -classpath $(BENCH_CLASS_CP) -jni -o $@ $(basename $(notdir $<))

libt3GT3.a:		t3GT3.c t3GT3.h
	$(LDSHARED) -o $@ -I. -I$(JAL_ROOT)/rvm/src/include -O -g $<

%.so:	%.a
	cp $< $@

sanity:		sanity-diff

# Status
#
#  tried                               problems
#  ------                              --------
#    +          testConstants
#    +          testStackAccess
#    +          testFieldAccess
#    +          testArrayAccess
#    +          testReturn
#    +          testCompare
#    +          testConversions
#    +          testArithmetic
#    +          testMath
#    +          testSwitch
#    +          testStaticCall
#    +          testVirtualCall
#    +          testInterfaceCall
#    +          testSpecialCall
#    +          testClassInitializer
#    +          testThrow
#    +          testFinally
#    +          testInstanceOf
#    +          testClone
#    +          testMonitorUnwind
#    +          testGC
#    !          testReflection          - missing native methods for array access
#    !          testClassLoading        - differs from jdk in exception treatment
#    !          testStackTrace          - output format differs from jdk (extra frames)
#    !          testMetaclass           - fails badly
