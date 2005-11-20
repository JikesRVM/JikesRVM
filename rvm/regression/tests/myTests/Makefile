#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
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
	$(CC) $(CFLAGS) -c -o $(basename $(notdir $<)).o -I. -I$(JAL_ROOT)/rvm/src/include $<
	$(LDSHARED) $(CXXFLAGS) -o $@ -I$(JAL_ROOT)/rvm/src/include $(basename $(notdir $<)).o

%.so:	%.a
	cp -f $< $@
