#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

###############################################################################
# generic benchmark framework setup
#
include			$(RVM_BUILD)/Make.rules

BENCH_NAME=Tomcat
BENCH_INCLUDE=Servlet
include			$(JAL_ROOT)/rvm/regression/Local.rules


###############################################################################
# tomcat's notion of home
#
TOMCAT_TEST_HOME=$(JAL_ROOT)/rvm/regression/tests/tomcat


###############################################################################
# Tomcat's contexts (i.e. the stuff it will be serving)
#
HOME_WAR_FILES=$(wildcard $(BENCH_HOME)/webapps/*.war)
WAR_FILES=$(subst $(BENCH_HOME), $(WORKING), $(HOME_WAR_FILES))

CONTEXT_DIRS=$(basename $(WAR_FILES))

WEB_INF_CLASS_PATH=$(foreach dir,$(CONTEXT_DIRS), $(dir)/WEB-INF/classes)

WORK_CLASS_PATH=$(foreach dir,$(WAR_FILES), $(WORKING)/work/localhost_8080%2F$(notdir $(basename $(dir))))

BENCH_CLASS_PATH=$(WEB_INF_CLASS_PATH) $(WORK_CLASS_PATH)


###############################################################################
# how to run (and stop) Tomcat
#
SERVER_NAME=org.apache.tomcat.startup.Tomcat
SERVER_ARGS="-home $(WORKING)"
SENTINEL="PoolTcpConnector: Starting HttpConnectionHandler"
STOP_NAME=org.apache.tomcat.startup.Tomcat
STOP_ARGS="-home $(WORKING) -stop"
HEAPSIZE=300
LHEAPSIZE=200
HTTP_PORT ?= $(shell $(FREEPORT_SH))
AJP12_PORT ?= $(shell $(FREEPORT_SH) 1)

###############################################################################
# the stress-test client
#
CLIENT_NAME=TestClient.Start
CLIENT_ARGS="-requests $(WORKING)/requests.xml -clients 10 -count 1000"

CLIENT_SOURCES=$(foreach FILE, $(notdir $(wildcard $(JAL_ROOT)/rvm/src/examples/tomcat/TestClient/*.java)), TestClient/$(FILE))
CLIENT_CLASSES=$(patsubst %.java,%.class,$(CLIENT_SOURCES))

BENCH_SOURCE_PATH=$(JAL_ROOT)/rvm/src/examples/tomcat
BENCH_CLASS_PATH+=$(HTTPClient_HOME)

###############################################################################
# include regression mechanisms
#
include			$(JAL_ROOT)/rvm/regression/Make.rules


###############################################################################
# rules for running
#
$(WORKING)/Makefile:	$(WORKING) $(TOMCAT_TEST_HOME)/Makefile
ifneq ($(WORKING),.)
	cp $@ $<
endif

$(WORKING)/conf:
ifneq ($(WORKING),.)
	mkdir -p $@
endif

$(WORKING)/conf/tomcat-users.xml:	$(TOMCAT_TEST_HOME)/conf/tomcat-users.xml $(WORKING)/conf
ifneq ($(WORKING),.)
	cp -f $< $@
endif

$(WORKING)/conf/server.xml:	$(TOMCAT_TEST_HOME)/conf/server.xml $(WORKING)/conf
ifneq ($(WORKING),.)
	@echo Generating server.xml
	@sed -e "s%value=\"8080\"%value=\"$(HTTP_PORT)\"%g" \
	     -e "s%value=\"8007\"%value=\"$(AJP12_PORT)\"%g" \
	  < $< \
	  > $@
endif

$(WORKING)/requests.xml:	$(TOMCAT_TEST_HOME)/requests.xml $(WORKING)/conf
ifneq ($(WORKING),.)
	@echo Generating requests.xml
	@sed -e "s%localhost:8080%localhost:$(HTTP_PORT)%g" < $< > $@
endif

$(WORKING)/webapps:
	mkdir -p $@

$(WORKING)/webapps/%.war:	$(BENCH_HOME)/webapps/%.war $(WORKING)/webapps
	cp -f $< $@

org/apache/tomcat/startup/Tomcat.class:	

TestClient/Start.class:	$(CLIENT_CLASSES)

input:
	mkdir -p $(WORKING)/expected
	cp -f $(TOMCAT_TEST_HOME)/expected/*Example* $(WORKING)/expected 

start:			input $(WAR_FILES) $(WORKING)/conf/server.xml $(WORKING)/conf/tomcat-users.xml

sanity:			input $(WAR_FILES) $(WORKING)/conf/server.xml $(WORKING)/conf/tomcat-users.xml $(WORKING)/requests.xml
	$(MAKE) server-sanity-check-rules

client-check:
	$(FGREP) -q "Downloaded 6247180 bytes in 10000 requests" $(OUT)
	$(FGREP) -q "Verified 10000 requests" $(OUT)

server-check:
	sleep 10
	$(FGREP) -qe '- ContextManager: Removing context Ctx(  )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /admin )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /examples )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /test )' $(OUT)
