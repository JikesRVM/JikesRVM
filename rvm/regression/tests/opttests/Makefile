#
# (C) Copyright IBM Corp. 2001
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

include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/opttests
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE = 80
EXPECTED=Tests.expected
START_NAME=InvokeReflect
START_ARGS=$(ALL_TESTS)

include		$(JAL_ROOT)/rvm/regression/Make.rules

sanity: 	$(foreach NAME, $(ALL_TESTS), $(NAME).class)
	$(MAKE) sanity-diff


