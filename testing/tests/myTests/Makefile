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
include		$(RVM_BUILD)/Make.rules.target

JCOMPILE=$(RVM_BUILD)/jbuild.tool

default:;

clean:
	$(RM) *.class

%.h:	%.class
	$(HOST_JAVA_HOME)/bin/javah -jni -o $@ $(basename $(notdir $<))

lib%.a:	%.c %.h
	$(CC) $(CFLAGS) -c -o $(basename $(notdir $<)).o -I. -I$(JAL_ROOT)/include $<
	$(LDSHARED) $(CXXFLAGS) -o $@ -I$(JAL_ROOT)/include $(basename $(notdir $<)).o

%.so:	%.a
	cp -f $< $@
