#
# (C) Copyright IBM Corp. 2001
#
# Performance benchmark - Runs a version of the OPT compiler as an application
#
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/OPTBench
BENCH_SOURCE_PATH=$(BENCH_HOME)
HEAPSIZE=200
LARGEHEAPSIZE=100
START_NAME=TestDriver

include		$(JAL_ROOT)/rvm/regression/Make.rules

# Compile Hello World
#
helloworld:	HelloWorld.class	
	$(MAKE) START_ARGS="$(MY_ARGS) class HelloWorld" bench

# Compile self
#
self:	
	$(MAKE) START_ARGS="$(MY_ARGS) -longcommandline self.classes" bench

# no actual sanity tests yet
#
sanity:	self
