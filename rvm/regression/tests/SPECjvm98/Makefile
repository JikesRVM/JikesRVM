#
# (C) Copyright IBM Corp. 2001
#
include         $(RVM_BUILD)/Make.rules

BENCH_NAME=SPECjvm98
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)
START_NAME=SpecApplication

ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE=400
  LHEAPSIZE=100
else
  HEAPSIZE=120
  LHEAPSIZE=50
endif

include 	$(JAL_ROOT)/rvm/regression/Make.rules

TESTS=_200_check _201_compress _202_jess _209_db _213_javac _222_mpegaudio _227_mtrt _228_jack


$(WORKING)/spec:	$(WORKING)
ifneq ($(WORKING),.)
	ln -fs $(BENCH_HOME)/spec $(WORKING)/spec
endif

$(WORKING)/props:	$(WORKING)
ifneq ($(WORKING),.)
	ln -fs $(BENCH_HOME)/props $(WORKING)/props
endif

$(WORKING)/Makefile:	$(WORKING) $(JAL_ROOT)/rvm/regression/tests/SPECjvm98/Makefile
ifneq ($(WORKING),.)
	cp -f $(JAL_ROOT)/rvm/regression/tests/SPECjvm98/Makefile $(WORKING)/Makefile
endif

check:
	@$(FGREP) "Finished" $(OUT) > /dev/null
	@$(FGREP) "Finished" $(OUT) | $(FGREP) -v "**NOT" $(OUT) > /dev/null
	@$(AWK) '/start\./{cnt++};/^-- Stack --/{cnt++};/Finished/{cnt--};END{exit cnt}' $(OUT)

ifeq ($(MODE),PERFORMANCE)

sanity:	$(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -g -m5 -M5 -s100 $(TESTS)" OUT=$(OUT) sanity-check-rule

do-gather-performance:
	$(JAL_ROOT)/rvm/regression/tests/SPECjvm98/parseSPECmark $(OUT) >> $(PERF_LOG)

else

$(TESTS): %: $(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -d3000 -g -m2 -M4 -s10 $@" OUT=$(OUT).$@ do-sanity-check-rule

sanity:	$(TESTS)
	@$(MAKE) START_NAME="SPECjvm98" do-sanity-finish

endif

