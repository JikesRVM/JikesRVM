#
# (C) Copyright IBM Corp. 2001
#
# @author Ton Ngo
JNI_INCLUDE = /opt/IBMJava2-13/include

LINKER   = ld -bnoentry \
               -blibpath:/usr/lib:/lib -bE:

DISASSEMBLER_NATIVE_CODE = IntelDisassembler.o disasm.o disbuf.o ihnpdsm.o

.SUFFIXES:	.c .C .o .java .h .class

.C.o:
	gcc -funsigned-bitfields -I. -I$(JNI_INCLUDE) -g -c $<

.c.o:
	gcc -funsigned-bitfields -I. -I$(JNI_INCLUDE) -g -c $<

.class.h:
	/opt/IBMJava2-13/bin/javah -jni -o $*.h $*

.java.class:
	/opt/IBMJava2-13/bin/javac $<


# this section builds the Java classes

#IntelDisassembler.class:	IntelDisassembler.java 
#
#IntelDisassembler.h:	IntelDisassembler.class
#
IntelDisassembler.o:	IntelDisassembler.c IntelDisassembler.h

IntelDisassembler.so:	$(DISASSEMBLER_NATIVE_CODE)
	grep " JNICALL " $*.h | sed "s/.* JNICALL //g">$@.exp
	gcc -shared -L/usr/lib -L/lib \
           -o $@ $(DISASSEMBLER_NATIVE_CODE)
	mv $@ lib$@
	rm $@.exp


# this section builds the disassembler 
disasm.o: disasm.C disasm.h
	gcc -funsigned-bitfields -g -c disasm.C

disbuf.o: disbuf.C
	gcc -funsigned-bitfields -g -c disbuf.C

dis: dis.c disasm.o disbuf.o ihnpdsm.o
	gcc -funsigned-bitfields -g -o dis dis.c disasm.o disbuf.o ihnpdsm.o
	./dis

clean:
	rm -f *.o *.class IntelDisassembler.h dis
