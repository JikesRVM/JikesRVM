#
# (C) Copyright IBM Corp. 2001
#
include         $(RVM_BUILD)/Make.rules

BENCH_NAME=jBYTEmark
BENCH_HOME=/homes/fury5/jalapeno/contrib/benchmarks/jBYTEmark-0.9+
BENCH_SOURCE_PATH=$(JAL_ROOT)/regression/tests/jBYTEmark:$(BENCH_HOME)
HEAPSIZE=70
START_NAME=jBYTEmark

include		$(JAL_ROOT)/regression/Make.rules

sanity:		sanity-check-rule

do-gather-performance:
	awk -f $(JAL_ROOT)/regression/tests/jBYTEmark/perf.awk $(OUT) >> $(PERF_LOG)

check:
	@$(FGREP) -q "FP Index:" < $(OUT)


