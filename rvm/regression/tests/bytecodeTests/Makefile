#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/bytecodeTests
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=150
START_NAME=TestAll
EXPECTED=TestAll.expected

include 	$(JAL_ROOT)/rvm/regression/Make.rules

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
