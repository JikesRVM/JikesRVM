#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Ton Ngo
include		$(RVM_BUILD)/Make.rules.target

DISASSEMBLER_NATIVE_CODE = IntelDisassembler.o disasm.o ihnpdsm.o

.SUFFIXES:	.c .C .o .java .h .class

.C.o:
	gcc -funsigned-bitfields -I. -I$(JAL_ROOT)/rvm/src/include -g -c $<

.c.o:
	gcc -funsigned-bitfields -I. -I$(JAL_ROOT)/rvm/src/include -g -c $<

.class.h:
	$(HOST_JAVA_HOME)/bin/javah -jni -o $*.h $*

default: IntelDisassembler.so 

# this section builds the Java classes
IntelDisassembler.o:	IntelDisassembler.c IntelDisassembler.h

IntelDisassembler.so:	$(DISASSEMBLER_NATIVE_CODE)
	$(LDSHARED) $(CXXFLAGS) -o lib$@ $(DISASSEMBLER_NATIVE_CODE)
	cp libIntelDisassembler.so libIntelDisassembler.a

# this section builds the disassembler 
disasm.o: disasm.C disasm.h
	gcc -funsigned-bitfields -g -c disasm.C

clean:
	rm -f *.o *.class IntelDisassembler.h dis
