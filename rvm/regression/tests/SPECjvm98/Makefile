#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby
#
include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=SPECjvm98
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)
START_NAME=SpecApplication

ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE=512
  INITIAL_HEAPSIZE=256
else
  HEAPSIZE=200
endif

include 	$(JAL_ROOT)/rvm/regression/Make.rules

TESTS=_200_check _201_compress _202_jess _209_db _213_javac _222_mpegaudio _227_mtrt _228_jack


$(WORKING)/spec:	$(WORKING)
	rm -f $(WORKING)/spec
	ln -fs $(BENCH_HOME)/spec $(WORKING)/spec

$(WORKING)/props:	$(WORKING)
	rm -f $(WORKING)/props
	ln -fs $(BENCH_HOME)/props $(WORKING)/props

$(WORKING)/Makefile:	$(WORKING) $(JAL_ROOT)/rvm/regression/tests/SPECjvm98/Makefile
ifneq ($(WORKING),.)
	cp -f $(JAL_ROOT)/rvm/regression/tests/SPECjvm98/Makefile $(WORKING)/Makefile
endif

check:
	@exit `$(FGREP) "Finished" $(OUT) | $(FGREP) "**NOT" $(OUT) | wc -l`
	@$(FGREP) "Finished" $(OUT) > /dev/null
	@$(FGREP) "Finished" $(OUT) | $(FGREP) -v "**NOT" $(OUT) > /dev/null
	@$(AWK) '/start\./{cnt++};/^-- Stack --/{cnt++};/Finished/{cnt--};END{exit cnt}' $(OUT)

ifeq ($(MODE),PERFORMANCE)

sanity:	$(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -g -m20 -M20 -s100 $(TESTS)" OUT=$(OUT) sanity-check-rule

do-gather-performance:
	$(JAL_ROOT)/rvm/regression/tests/SPECjvm98/parseSPECmark $(OUT) >> $(PERF_LOG)

else
ifeq ($(MODE),MEASURE_COMPILATION)

sanity:	$(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -g -m1 -M1 -s10 $(TESTS)" OUT=$(OUT) sanity-check-rule

else

$(TESTS): %: $(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -d3000 -g -m2 -M4 -s10 $@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) START_NAME="SPECjvm98" do-sanity-finish

endif
endif

