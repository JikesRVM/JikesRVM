#
# (C) Copyright IBM Corp. 2001
#
# $Id:
#
# @author Peter Sweeney

include		$(RVM_BUILD)/Make.rules
 
BENCH_NAME=pseudojbb
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_JARS=jbb.jar jbb_no_precompile.jar check.jar reporter.jar
BENCH_CLASS_PATH=$(foreach JAR, $(BENCH_JARS), $(BENCH_HOME)/$(JAR))

vpath %.jar	$(BENCH_HOME)

START_NAME=spec.jbb.JBBmain

HEAPSIZE=400
LHEAPSIZE=200

include		$(JAL_ROOT)/rvm/regression/Make.rules

#
# sanity test support
#

ifeq ($(MODE),PERFORMANCE)
sanity: 	$(WORKING)/pseudojbb.perf.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.perf.props" sanity-check-rule
else
sanity: 	$(WORKING)/pseudojbb.sanity.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.sanity.props" sanity-check-rule
endif


ifeq ($(MODE),PERFORMANCE)
check:
	@$(FGREP) -q "JVM Check OK" < $(OUT)
	@$(FGREP) -q "Run will be COMPLIANT" < $(OUT)
	@$(FGREP) -q "No errors.  Valid run." < $(OUT)
	@$(FGREP) -q "Valid run, Score is" < $(OUT)
else
check:
	@$(FGREP) -q "JVM Check OK" < $(OUT)
	@egrep -q "(Valid run, Score is)|(INVALID run; Score is  Invalid, but estimate is)" < $(OUT)
endif

#
# performance runs
#
do-gather-performance:
	$(AWK) -f $(JAL_ROOT)/rvm/regression/tests/pseudojbb/summarize.awk $(OUT) >> $(PERF_LOG)


#
# big runs to push performance off a cliff :)
#

bigrun:		$(WORKING)/pseudojbb.big.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.big.props" bench-rvm

bigrun-jdk:	$(WORKING)/pseudojbb.big.props $(WORKING)/pseudojbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile pseudojbb.big.props" bench-jdk


#
# server application harness
#

$(RESULT): 
	$(MAKE) OUT=$(RESULT) 

start:

finish:

#
# misc.  
#
spec/jbb/JBBmain.class:		jbb.jar

$(WORKING)/%.props:		$(JAL_ROOT)/rvm/regression/tests/pseudojbb/%.props $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif

$(WORKING)/Makefile:		$(JAL_ROOT)/rvm/regression/tests/pseudojbb/Makefile $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif
