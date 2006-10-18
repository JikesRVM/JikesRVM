#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

ALL_TESTS	= fibo tak_int tak_long tak_fp tak_db table TestLookupSwitch \
	  DeBruijn hello virtual sieve hanoi logic unresolved stPutGet \
	  virtUnPutGet virtUnresolved interf CheckStore \
	  TypeCheck TestArithmetic TestArrayAccess TestCall \
	  TestCompare TestConstants TestConversions TestFieldAccess \
	  TestInterfaceCall TestMath TestReturn \
	  TestSpecialCall TestStackAccess TestSwitch \
	  TestUnresolved TestInstanceOf TestExceptionThrow TestFinally \
	  inlineDeep inlineExcept 

TIME_LIMIT=400

include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/testing/tests/opttests
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE = 150
EXPECTED=Tests.expected
START_NAME=InvokeReflect
START_ARGS=$(ALL_TESTS)

include		$(JAL_ROOT)/testing/harness/Make.rules

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) TEST_NAME=opttests sanity-diff


