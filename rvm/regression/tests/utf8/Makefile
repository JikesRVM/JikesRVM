#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules.target

BENCH_HOME=$(JAL_ROOT)/rvm/src/examples/utf8
BENCH_SOURCE_PATH=$(WORKING) $(BENCH_HOME)
HEAPSIZE=150
LARGEHEAPSIZE=30
START_NAME=utf8test

include $(JAL_ROOT)/rvm/regression/Make.rules

$(WORKING)/utf8test.java:	utf8testdriver.class
		$(HOST_VM_RT) -classpath $(BENCH_CLASS_CP) utf8testdriver > $(WORKING)/utf8test.java

sanity:		sanity-compare

fullclean:;	$(RM) utf8test.java utf8test.expected

