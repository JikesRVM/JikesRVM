#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_BUILD)/Make.rules
 
BENCH_NAME=SPECjbb2000
BENCH_HOME=/homes/fury5/jalapeno/contrib/benchmarks/SPECjbb2000

BENCH_JARS=jbb.jar jbb_no_precompile.jar check.jar reporter.jar
BENCH_CLASS_PATH=$(foreach JAR, $(BENCH_JARS), $(BENCH_HOME)/$(JAR))

vpath %.jar	$(BENCH_HOME)

START_NAME=spec.jbb.JBBmain

HEAPSIZE=500
LHEAPSIZE=300

include		$(JAL_ROOT)/regression/Make.rules

#
# sanity test support
#

ifeq ($(MODE),PERFORMANCE)
sanity: 	$(WORKING)/SPECjbb.perf.props $(WORKING)/SPECjbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile SPECjbb.perf.props" sanity-check-rule
else
sanity: 	$(WORKING)/SPECjbb.sanity.props $(WORKING)/SPECjbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile SPECjbb.sanity.props" sanity-check-rule
endif

check:
	@$(FGREP) -q "JVM Check OK" < $(OUT)
	@$(FGREP) -q "Run will be COMPLIANT" < $(OUT)
	@$(FGREP) -q "No errors.  Valid run." < $(OUT)
	@$(FGREP) -q "Valid run, Score is" < $(OUT)

#
# performance runs
#
do-gather-performance:
	$(AWK) -f $(JAL_ROOT)/regression/tests/SPECjbb2000/summarize.awk $(OUT) >> $(PERF_LOG)


#
# big runs to push performance off a cliff :)
#

bigrun:		$(WORKING)/SPECjbb.big.props $(WORKING)/SPECjbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile SPECjbb.big.props" bench-rvm

bigrun-jdk:	$(WORKING)/SPECjbb.big.props $(WORKING)/SPECjbb_config.props $(WORKING)/Makefile
	$(MAKE) START_ARGS="-propfile SPECjbb.big.props" bench-jdk


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

$(WORKING)/%.props:		$(JAL_ROOT)/regression/tests/SPECjbb2000/%.props $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif

$(WORKING)/Makefile:		$(JAL_ROOT)/regression/tests/SPECjbb2000/Makefile $(WORKING)
ifneq ($(WORKING),.)
	cp $< $@
	chmod a+rw $@
endif
