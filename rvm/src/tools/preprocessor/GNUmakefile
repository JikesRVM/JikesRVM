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

#$(TOOL): ${TOOL_NAME}.c
#     $(CC) --std=gnu99 $(CFLAGS) $< -o $@

$(TOOL): ${TOOL_NAME}.C
	$(CXX) $(CXXFLAGS) $< -o $@

# Preprocessor testing.
# These rules make the executable anywhere; usually here if you're testing.

.PHONY: tool test test1 test2 test3 test4 test5 test6 cleantest clean
test:			${TOOL} test1 test2 test3 test4 test5 test6
	@echo "All tests passed."

test1: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DX=1 >test1.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -DX=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test1.actual
	diff test1.actual test1.expected

test2: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DY=1 >test2.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -DY=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test2.actual
	diff test2.actual test2.expected

test3: ${TOOL}
	sed 's@//-@@' testSimple.preprocessor | ${CPP} -DZ=1 >test3.expected
	$(RM) /tmp/testSimple.preprocessor
	${TEST_TOOL} -DZ=1 /tmp testSimple.preprocessor
	grep -v '^$$' /tmp/testSimple.preprocessor >test3.actual
	diff test3.actual test3.expected

test4: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DC=1 >test4.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test4.actual
	diff test4.actual test4.expected

test5: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DC=1 >test5.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -DA=1 -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test5.actual
	diff test5.actual test5.expected

test6: ${TOOL}
	sed 's@//-@@' testFancy.preprocessor | ${CPP} -DA=1 -DB=1 -DC=1 >test6.expected
	$(RM) /tmp/testFancy.preprocessor
	${TEST_TOOL} -DA=1 -DB=1 -DC=1 /tmp testFancy.preprocessor
	grep -v '^$$' /tmp/testFancy.preprocessor >test6.actual
	diff test6.actual test6.expected

cleantest:
	$(RM) ${TOOL} *.o test*.actual test*.expected /tmp/test*.preprocessor

clean:
	$(RM) ${TOOL} ${TOOL}.o

