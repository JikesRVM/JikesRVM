#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
# @author Stephen Fink

include         $(RVM_BUILD)/Make.rules.target

BENCH_NAME=Xalan
include		$(JAL_ROOT)/testing/harness/Local.rules

HEAPSIZE=250

DRIVER_SOURCE_PATH=$(JAL_ROOT)/testing/tests/iterationDriver
BENCH_SOURCE_PATH=$(DRIVER_SOURCE_PATH)
BENCH_CLASS_PATH=$(BENCH_HOME)/xalan.jar:$(BENCH_HOME)/xercesImpl.jar:$(BENCH_HOME)/xml-apis.jar:$(BENCH_HOME)/serializer.jar:$(BENCH_HOME)/samples/xalansamples.jar

ifeq ($(MODE),PERFORMANCE)
  ITERATIONS=11
  TIME_LIMIT=900
else
  ITERATIONS=1
  TIME_LIMIT=300
endif

START_NAME=Driver
START_ARGS=$(ITERATIONS) SimpleTransform main $(INPUT_FILES)
BENCH_RVM_ARGS=-Djavax.xml.transform.TransformerFactory=org.apache.xalan.xsltc.trax.TransformerFactoryImpl -Djavax.xml.parsers.SAXParserFactory=org.apache.xerces.jaxp.SAXParserFactoryImpl

default:	sanity

$(WORKING)/birds.xsl:		$(WORKING)
ifneq ($(WORKING),.)
	cp -f $(BENCH_HOME)/samples/SimpleTransform/birds.xsl $(WORKING)
endif

$(WORKING)/birds.xml:		$(WORKING)
ifneq ($(WORKING),.)
	cp -f $(BENCH_HOME)/samples/SimpleTransform/birds.xml $(WORKING)
endif


sanity: $(WORKING)/birds.xml $(WORKING)/birds.xsl
	$(MAKE) sanity-check-rule

include		$(JAL_ROOT)/testing/harness/Make.rules

do-gather-performance:
	 @$(AWK) -f $(JAL_ROOT)/testing/harness/tests/xerces/perf.awk $(OUT) >> $(PERF_LOG)

check:  
	@$(FGREP) -q "The result is in birds.out" < $(OUT)
