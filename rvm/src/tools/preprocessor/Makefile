#
# (C) Copyright IBM Corp. 2001
#
#$Id$
# @author Derek Lieber

include		$(RVM_ROOT)/rvm/build.Make.rules


tool:
	@$(MAKE) $(SCRATCH_DIR)/preprocessModifiedFiles

$(SCRATCH_DIR)/preprocessModifiedFiles:	preprocessModifiedFiles.C
	$(CPLUS) preprocessModifiedFiles.C -o $(SCRATCH_DIR)/preprocessModifiedFiles


# Preprocessor testing.
# These rules make the executable here.
test:			preprocessModifiedFiles test1 test2 test3 test4 test5 test6

test1:;			sed 's@//-@@' testSimple.preprocessor | /lib/cpp -P -DX=1 >test1.expected
			rm -f /tmp/testSimple.preprocessor
			preprocessModifiedFiles -DX=1 /tmp testSimple.preprocessor || true
			grep -v '^$$' /tmp/testSimple.preprocessor >test1.actual
			diff test1.actual test1.expected

test2:;			sed 's@//-@@' testSimple.preprocessor | /lib/cpp -P -DY=1 >test2.expected
			rm -f /tmp/testSimple.preprocessor
			preprocessModifiedFiles -DY=1 /tmp testSimple.preprocessor || true
			grep -v '^$$' /tmp/testSimple.preprocessor >test2.actual
			diff test2.actual test2.expected

test3:;			sed 's@//-@@' testSimple.preprocessor | /lib/cpp -P -DZ=1 >test3.expected
			rm -f /tmp/testSimple.preprocessor
			preprocessModifiedFiles -DZ=1 /tmp testSimple.preprocessor || true
			grep -v '^$$' /tmp/testSimple.preprocessor >test3.actual
			diff test3.actual test3.expected

test4:;			sed 's@//-@@' testFancy.preprocessor | /lib/cpp -P -DC=1 >test4.expected
			rm -f /tmp/testFancy.preprocessor
			preprocessModifiedFiles -DC=1 /tmp testFancy.preprocessor || true
			grep -v '^$$' /tmp/testFancy.preprocessor >test4.actual
			diff test4.actual test4.expected

test5:;			sed 's@//-@@' testFancy.preprocessor | /lib/cpp -P -DA=1 -DC=1 >test5.expected
			rm -f /tmp/testFancy.preprocessor
			preprocessModifiedFiles -DA=1 -DC=1 /tmp testFancy.preprocessor || true
			grep -v '^$$' /tmp/testFancy.preprocessor >test5.actual
			diff test5.actual test5.expected

test6:;			sed 's@//-@@' testFancy.preprocessor | /lib/cpp -P -DA=1 -DB=1 -DC=1 >test6.expected
			rm -f /tmp/testFancy.preprocessor
			preprocessModifiedFiles -DA=1 -DB=1 -DC=1 /tmp testFancy.preprocessor || true
			grep -v '^$$' /tmp/testFancy.preprocessor >test6.actual
			diff test6.actual test6.expected

cleantest:;		rm -f preprocessModifiedFiles *.o test*.actual test*.expected /tmp/test*.preprocessor

clean:;			@rm -f preprocessModifiedFiles preprocessModifiedFiles.o

