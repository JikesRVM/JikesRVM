#
# (C) Copyright IBM Corp. 2001
#
include         $(RVM_BUILD)/Make.rules

BENCH_NAME=SPECjvm98
BENCH_HOME=/homes/fury5/jalapeno/contrib/benchmarks/SPECjvm98
BENCH_CLASS_PATH=$(BENCH_HOME)
START_NAME=SpecApplication

ifeq ($(MODE),PERFORMANCE)
  HEAPSIZE=400
  LHEAPSIZE=100
else
  HEAPSIZE=120
  LHEAPSIZE=50
endif

include 	$(JAL_ROOT)/regression/Make.rules

TESTS=_200_check _201_compress _202_jess _209_db _213_javac _222_mpegaudio _227_mtrt _228_jack


$(WORKING)/spec:	$(WORKING)
	ln -fs $(BENCH_HOME)/spec $(WORKING)/spec

$(WORKING)/props:	$(WORKING)
	ln -fs $(BENCH_HOME)/props $(WORKING)/props

$(WORKING)/Makefile:	$(WORKING) $(JAL_ROOT)/regression/tests/SPECjvm98/Makefile
	cp -f $(JAL_ROOT)/regression/tests/SPECjvm98/Makefile $(WORKING)/Makefile


$(TESTS): %: $(WORKING)/spec $(WORKING)/props $(WORKING)/Makefile
	cd $(WORKING); $(MAKE) START_ARGS="-a -d3000 -g -m2 -M4 -s10 $@" OUT=$(OUT).$@ do-sanity-check-rule

check:
	@$(FGREP) -v "**NOT" $(OUT) > /dev/null
	@$(AWK) '/start\./{cnt++};/^-- Stack --/{cnt++};/Finished/{cnt--};END{exit cnt}' $(OUT)

ifeq ($(MODE),PERFORMANCE)
sanity:	
	cd $(WORKING); $(MAKE) START_ARGS="-a -g -m5 -M5 -s100 $(TESTS)" OUT=$(OUT) do-sanity-check-rule
else
sanity:	$(TESTS)
	@$(MAKE) START_NAME="SPECjvm98" do-sanity-finish
endif

do-gather-performance:
	$(JAL_SHADOW)/regression/tests/SPECjvm98/parseSPECmark $(OUT) >> $(PERF_LOG)

