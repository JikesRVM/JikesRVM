#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#
include		$(RVM_BUILD)/Make.rules

BENCH_NAME=VolanoMark
include		$(JAL_ROOT)/rvm/regression/Local.rules

BENCH_CLASS_PATH=$(BENCH_HOME)

USERS ?= 10
ROOMS ?= 10

HEAPSIZE=200
LARGEHEAPSIZE=100

SERVER_NAME=COM.volano.Main
SENTINEL="VolanoChatPro - unlimited connections"
STOP_NAME=COM.volano.Shutdown

ADMIN_PORT ?= $(shell $(FREEPORT_SH))
SERVER_PORT ?= $(shell $(FREEPORT_SH) 1)
SERVLET_PORT ?= $(shell $(FREEPORT_SH) 2)

ifeq ($(MODE),PERFORMANCE)
CLIENT_NAME=COM.volano.Mark
CLIENT_ARGS="-port $(SERVER_PORT) -rooms $(ROOMS) -users $(USERS) -count 100 -run"
else
CLIENT_NAME=COM.volano.Test
CLIENT_ARGS="-port $(SERVER_PORT) -rooms 10 -users 10 -loops 5 -run"
endif

include		$(JAL_ROOT)/rvm/regression/Make.rules

$(WORKING)/properties.txt:	$(BENCH_HOME)/properties.txt $(WORKING)
	@echo Generating properties.txt
	@sed -e "s%access.txt%$(BENCH_HOME)/access.txt%g" \
             -e "s%rooms.txt%$(BENCH_HOME)/rooms.txt%g" \
             -e "s%admin.port=8001%admin.port=$(ADMIN_PORT)%g" \
             -e "s%server.port=8000%server.port=$(SERVER_PORT)%g" \
	  < $(BENCH_HOME)/properties.txt \
	  > $@

$(WORKING)/httpd.txt:		$(BENCH_HOME)/httpd.txt $(WORKING)
	@echo Generating httpd.txt
	@sed -e "s%server.port=8080%server.port=$(SERVLET_PORT)%g" \
	  < $(BENCH_HOME)/httpd.txt \
	  > $@

$(RVM_BUILD)/RVM.classes/java/util/Observable.class:	$(HOST_REPOSITORIES)
	@echo Work around java.util.Observable bug in Volano
	@cd $(RVM_BUILD)/RVM.classes/; $(UNZIP_CMD) -qqoU $(HOST_REPOSITORIES) java/util/Observable.class > /dev/null 2>&1

client-check:
ifneq ($(MODE),PERFORMANCE)
	@awk -f ignore.awk $(OUT) | diff - Volano.client.expected
endif

server-check:
	@awk -f ignore.awk $(OUT) | diff - Volano.server.expected

sanity:		$(RVM_BUILD)/RVM.classes/java/util/Observable.class
	rm -f $(WORKING)/properties.txt $(WORKING)/httpd.txt
	$(MAKE) SERVER_PORT=$(SERVER_PORT) $(WORKING)/properties.txt $(WORKING)/httpd.txt 
	$(MAKE) SERVER_PORT=$(SERVER_PORT) server-sanity-check-rules

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
