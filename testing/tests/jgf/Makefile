#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
#
# @author Julian Dolby
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=JGF
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=100

ifeq ($(MODE),PERFORMANCE)

SIZE=1
DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
EXTRA_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
START_NAME=Driver
START_ARGS=2 JGF_SerialBenchmark main ${SIZE}
TIME_LIMIT=7200

else

SIZE=0
START_NAME=JGF_SerialBenchmark
START_ARGS=${SIZE}
TIME_LIMIT=3600

endif

BENCH_SOURCE_PATH=$(JAL_ROOT)/testing/tests/jgf/src:$(BENCH_HOME):$(BENCH_HOME)/section1:$(BENCH_HOME)/section2:$(BENCH_HOME)/section3:${EXTRA_SOURCE_PATH}

ALL_CLASSES	= jgfutil/JGFInstrumentor \
                  jgfutil/JGFSection1 \
                  jgfutil/JGFSection2 \
                  jgfutil/JGFSection3 \
                  jgfutil/JGFTimer \
                  JGFArithBench \
                  JGFAssignBench \
                  JGFCastBench \
                  JGFCreateBench \
                  JGFExceptionBench \
                  JGFLoopBench \
                  JGFMathBench \
                  JGFMethodBench \
                  JGFSerialBench \
                  crypt/IDEATest \
                  crypt/JGFCryptBench \
                  fft/FFT \
                  fft/JGFFFTBench \
                  heapsort/JGFHeapSortBench \
                  heapsort/NumericSortTest \
                  lufact/JGFLUFactBench \
                  lufact/Linpack \
                  series/JGFSeriesBench \
                  series/SeriesTest \
                  sor/JGFSORBench \
                  sor/SOR \
                  sparsematmult/JGFSparseMatmultBench \
                  sparsematmult/SparseMatmult \
                  euler/JGFEulerBench \
                  euler/Tunnel \
                  moldyn/JGFMolDynBench \
                  moldyn/md \
                  montecarlo/AppDemo \
                  montecarlo/CallAppDemo \
                  montecarlo/DemoException \
                  montecarlo/JGFMonteCarloBench \
                  montecarlo/MonteCarloPath \
                  montecarlo/PathId \
                  montecarlo/PriceStock \
                  montecarlo/RatePath \
                  montecarlo/ReturnPath \
                  montecarlo/ToInitAllTasks \
                  montecarlo/ToResult \
                  montecarlo/ToTask \
                  montecarlo/Universal \
                  montecarlo/Utilities \
                  raytracer/Interval \
                  raytracer/Isect \
                  raytracer/JGFRayTracerBench \
                  raytracer/Light \
                  raytracer/Primitive \
                  raytracer/Ray \
                  raytracer/RayTracer \
                  raytracer/Scene \
                  raytracer/Sphere \
                  raytracer/Surface \
                  raytracer/Vec \
                  raytracer/View \
                  search/ConnectFourConstants \
                  search/Game \
                  search/JGFSearchBench \
                  search/SearchGame \
                  search/TransGame \
                  JGF_SerialBenchmark

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class)
	$(MAKE) sanity-check-rule

include		$(JAL_ROOT)/testing/harness/Make.rules


do-gather-performance:

check:
