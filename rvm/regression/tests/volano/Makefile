#
# (C) Copyright IBM Corp. 2001
#
include		$(RVM_BUILD)/Make.rules

BENCH_NAME=Volano
BENCH_HOME=/homes/fury5/jalapeno/contrib/benchmarks/VolanoMark-2.1.2
BENCH_CLASS_PATH=$(BENCH_HOME)

USERS ?= 10
ROOMS ?= 10

HEAPSIZE=200
LARGEHEAPSIZE=100

SERVER_NAME=COM.volano.Main
SENTINEL="VolanoChatPro - unlimited connections"
STOP_NAME=COM.volano.Shutdown

ifeq ($(MODE),PERFORMANCE)
CLIENT_NAME=COM.volano.Mark
CLIENT_ARGS="-rooms $(ROOMS) -users $(USERS) -count 100 -run"
else
CLIENT_NAME=COM.volano.Test
CLIENT_ARGS="-rooms 10 -users 10 -loops 5 -run"
endif

include		$(JAL_ROOT)/regression/Make.rules

$(WORKING)/properties.txt:	$(BENCH_HOME)/properties.txt $(WORKING)
	@echo Generating properties.txt
	@sed -e "s%httpd.txt%$(BENCH_HOME)/httpd.txt%g" \
	     -e "s%access.txt%$(BENCH_HOME)/access.txt%g" \
             -e "s%rooms.txt%$(BENCH_HOME)/rooms.txt%g" \
	  < $(BENCH_HOME)/properties.txt \
	  > $@

$(RVM_BUILD)/Jalapeno.classes/java/util/Observable.class:	$(HOST_REPOSITORIES)
	@echo Work around java.util.Observable bug in Volano
	@cd $(RVM_BUILD)/Jalapeno.classes/; unzip -qqoU $(HOST_REPOSITORIES) java/util/Observable.class

client-check:
ifneq ($(MODE),PERFORMANCE)
	@awk -f ignore.awk $(OUT) | diff - Volano.client.expected
endif

server-check:
	@awk -f ignore.awk $(OUT) | diff - Volano.server.expected

sanity:		$(WORKING)/properties.txt $(RVM_BUILD)/Jalapeno.classes/java/util/Observable.class
	$(MAKE) server-sanity-check-rules

do-gather-performance:
	echo -n "Bottom Line: " >> $(PERF_LOG)
	fgrep throughput $(OUT).client >> $(PERF_LOG)

roomperf:
ifneq ($(ROOMS),1)
	$(MAKE) ROOMS=`expr $(ROOMS) / 2` roomperf
endif
	$(MAKE) MODE=PERFORMANCE client


roomtest:
	$(MAKE) clean
	$(MAKE) start	
	$(MAKE) MODE=TEST client
	$(MAKE) roomperf
	$(MAKE) finish


userperf:
ifneq ($(USERS),1)
	$(MAKE) USERS=`expr $(USERS) / 2` userperf
endif
	$(MAKE) MODE=PERFORMANCE client


usertest:
	$(MAKE) clean
	$(MAKE) start	
	$(MAKE) MODE=TEST client
	$(MAKE) roomperf
	$(MAKE) finish
