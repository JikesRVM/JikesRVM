#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_BUILD)/Make.rules

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/utf8
BENCH_SOURCE_PATH=$(WORKING) $(BENCH_HOME)
HEAPSIZE=50
LARGEHEAPSIZE=30
START_NAME=utf8test

include $(JAL_ROOT)/rvm/regression/Make.rules

$(WORKING)/utf8test.java:	utf8testdriver.class
		$(HOST_JAVA) -classpath $(BENCH_CLASS_CP) utf8testdriver > $(WORKING)/utf8test.java

sanity:		sanity-compare

fullclean:;	$(RM) utf8test.java utf8test.expected

