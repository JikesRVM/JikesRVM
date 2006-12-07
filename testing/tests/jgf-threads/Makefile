#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id: /jikesrvm/local/testing/tests/jgf/Makefile 10723 2006-12-03T03:56:55.303892Z peter  $
#
# @author Julian Dolby
#

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=JGF_THREADS
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=200

ifeq ($(MODE),PERFORMANCE)

SIZE=1
THREAD_COUNT=100
DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
EXTRA_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
START_NAME=Driver
START_ARGS=2 JGF_MultithreadedBenchmark main ${SIZE} ${THREAD_COUNT}
TIME_LIMIT=14400

else

SIZE=0
THREAD_COUNT=100
START_NAME=JGF_MultithreadedBenchmark
START_ARGS=${SIZE} ${THREAD_COUNT}
TIME_LIMIT=7200

endif

BENCH_SOURCE_PATH=$(JAL_ROOT)/testing/tests/jgf-threads/src:$(BENCH_HOME):$(BENCH_HOME)/section1:$(BENCH_HOME)/section2:$(BENCH_HOME)/section3:${EXTRA_SOURCE_PATH}

ALL_CLASSES	= jgfutil/JGFInstrumentor \
                  jgfutil/JGFSection1 \
                  jgfutil/JGFSection2 \
                  jgfutil/JGFSection3 \
                  jgfutil/JGFTimer \
                  JGFBarrierBench \
                  JGFSyncBench \
                  TournamentBarrier \
                  JGFForkJoinBench \
                  Barrier \
                  sor/JGFSORBench \
                  sor/SOR \
                  crypt/IDEATest \
                  crypt/JGFCryptBench \
                  lufact/TournamentBarrier \
                  lufact/JGFLUFactBench \
                  lufact/LinpackRunner \
                  lufact/Linpack \
                  lufact/Barrier \
                  sparsematmult/SparseMatmult \
                  sparsematmult/JGFSparseMatmultBench \
                  series/SeriesTest \
                  series/JGFSeriesBench \
                  raytracer/View \
                  raytracer/RayTracer \
                  raytracer/Primitive \
                  raytracer/Scene \
                  raytracer/TournamentBarrier \
                  raytracer/Ray \
                  raytracer/Light \
                  raytracer/Interval \
                  raytracer/Isect \
                  raytracer/Sphere \
                  raytracer/Surface \
                  raytracer/JGFRayTracerBench \
                  raytracer/Barrier \
                  raytracer/Vec \
                  moldyn/TournamentBarrier \
                  moldyn/md \
                  moldyn/JGFMolDynBench \
                  moldyn/Barrier \
                  montecarlo/MonteCarloPath \
                  montecarlo/PriceStock \
                  montecarlo/PathId \
                  montecarlo/ToTask \
                  montecarlo/Utilities \
                  montecarlo/Universal \
                  montecarlo/AppDemo \
                  montecarlo/RatePath \
                  montecarlo/DemoException \
                  montecarlo/CallAppDemo \
                  montecarlo/ReturnPath \
                  montecarlo/ToResult \
                  montecarlo/JGFMonteCarloBench \
                  JGF_MultithreadedBenchmark

${WORKING}/Data/hitData:
	mkdir ${WORKING}/Data
	cp $(BENCH_HOME)/section3/Data/hitData ${WORKING}/Data

sanity: 	$(foreach NAME, $(ALL_CLASSES), $(NAME).class) ${WORKING}/Data/hitData
	$(MAKE) sanity-check-rule

include		$(JAL_ROOT)/testing/harness/Make.rules


do-gather-performance:

check:
