#
# (C) Copyright IBM Corp. 2001, 2003
#
#$Id$
# @author Derek Lieber
# @modified Steven Augart

include		$(RVM_ROOT)/rvm/build.Make.rules

# This only affects testing, since SCRATCH_DIR will only be reset 
# if we don't have a value (whether command-line or from the environment).

## We need an explicit SCRATCH_DIR of ., so that we can work even for
## folks who don't put "." in their PATH variables.
SCRATCH_DIR?=.

TOOL_NAME=preprocessModifiedFiles
TOOL:=$(SCRATCH_DIR)/${TOOL_NAME}
TEST_TOOL:=$(TOOL) --disable-modification-exit-status
# -P means "do not generate #line directives"
# -C means to leave the text of comments in the output.
CPP:=/lib/cpp -P -C


tool:
	@$(MAKE) $(TOOL)

## We can compile this either as C or as C++.
## Maybe one compiler generates nicer warnings than the other?

# $(TOOL): ${TOOL_NAME}.c
#     $(CC) --std=gnu99 $(CFLAGS) $< -o $@

$(TOOL): ${TOOL_NAME}.C
	$(CXX) $(CXXFLAGS) $< -o $@

# Preprocessor testing.
# These rules make the executable anywhere; usually here if you're testing.

TESTS:=test1 test2 test3 test4 test5 test6 test7a test7b test8a test8b test8c test8d test9 test10
.PHONY: tool test $(TESTS) cleantest clean

test:			${TOOL} $(TESTS)
	@echo "**************************************************"
	@echo "			All tests passed."
	@echo "**************************************************"

test1: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DX=1 >test1.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DX=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test1.actual
	diff test1.actual test1.expected

test2: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DY=1 >test2.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DY=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test2.actual
	diff test2.actual test2.expected

test3: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DZ=1 >test3.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DZ=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test3.actual
	diff test3.actual test3.expected

test4: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DC=1 >test4.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test4.actual
	diff test4.actual test4.expected

test5: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DC=1 >test5.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DA=1 -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test5.actual
	diff test5.actual test5.expected

test6: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 -DC=1 >test6.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DA=1 -DB=1 -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test6.actual
	diff test6.actual test6.expected

## Leave out defn. of C:
test7a: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 >test7a.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -undefined-constants-in-conditions -DA=1 -DB=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test7a.actual
	diff test7a.actual test7a.expected

## Leave out defn. of C:
test7b: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 >test7b.expected
	$(RM) /tmp/testFancy.preprocessor
	{ ${TEST_TOOL} -DA=1 -DB=1 --no-undefined-constants-in-conditions --only-boolean-constants-in-conditions /tmp testFancy.preprocessor; (( $$? == 2 )) ; }
#	grep -v '^$$' /tmp/testFancy.preprocessor >test7b.actual
#	diff test7b.actual test7b.expected

test8a: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 -DC=1 >test8a.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -DA=1 -DB=1 -DC=1 -undefined-constants-in-conditions /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test8a.actual
	diff test8a.actual test8a.expected

test8b: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 -DC=99 >test8b.expected
	$(RM) /tmp/testFancy.preprocessor
# Trigger the error on D, which is undefined.
	{ ${TEST_TOOL} --no-undefined-constants-in-conditions --only-boolean-constants-in-conditions -DA=1 -DB=1 -DC=99 /tmp testFancy.preprocessor; (( $$? == 2 )) ; }
#	grep -v '^$$' /tmp/testFancy.preprocessor >test8b.actual
#	diff test8b.actual test8b.expected

test8c: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 -DC=99 >test8c.expected
	$(RM) /tmp/testFancy.preprocessor
# DON'T trigger the error on D, even though it is undefined.
	{ ${TEST_TOOL} --undefined-constants-in-conditions --no-only-boolean-constants-in-conditions -DA=1 -DB=1 -DC=99 /tmp testFancy.preprocessor; }
#	grep -v '^$$' /tmp/testFancy.preprocessor >test8c.actual
#	diff test8c.actual test8c.expected

test8d: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=0 -DC=99 >test8d.expected
	$(RM) /tmp/testFancy.preprocessor
# Trigger the error on C, which has value 99.
# Also complain about D, which is undefined
	{ ${TEST_TOOL} -k --no-undefined-constants-in-conditions --only-boolean-constants-in-conditions -DA=1 -DB=1 -DC=99 -DD=0 /tmp testFancy.preprocessor; (( $$? == 2 )) ; }
	! [[ -f /tmp/testFancy.preprocessor ]]

test9: ${TOOL}
	{ ${TEST_TOOL} -DA=1 -DB=1 /tmp testNoEndif.preprocessor; (( $$? == 2 )) ; }

test10: ${TOOL}
	{ ${TEST_TOOL} -DA=1 -DB=1 /tmp testNoSuchFile.preprocessor; (( $$? == 2 )) ; }

cleantest:
	$(RM) ${TOOL} *.o test*.actual test*.expected /tmp/test*.preprocessor

clean:
	$(RM) ${TOOL} ${TOOL}.o

