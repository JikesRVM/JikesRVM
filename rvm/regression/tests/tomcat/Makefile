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

###############################################################################
# the stress-test client
#
CLIENT_NAME=TestClient.Start
CLIENT_ARGS="-requests $(TOMCAT_TEST_HOME)/requests.xml -clients 10 -count 1000"

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

$(WORKING)/conf/%.xml:	$(TOMCAT_TEST_HOME)/conf/%.xml $(WORKING)/conf
ifneq ($(WORKING),.)
	cp -f $< $@
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

sanity:			input $(WAR_FILES) $(WORKING)/conf/server.xml $(WORKING)/conf/tomcat-users.xml
	$(MAKE) server-sanity-check-rules

client-check:
	$(FGREP) -q "Downloaded 7225060 bytes in 10000 requests" $(OUT)
	$(FGREP) -q "Verified 5430 requests" $(OUT)

server-check:
	sleep 10
	$(FGREP) -qe '- ContextManager: Removing context Ctx(  )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /admin )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /examples )' $(OUT)
	$(FGREP) -qe '- ContextManager: Removing context Ctx( /test )' $(OUT)
