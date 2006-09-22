#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001, 2005
#
# $Id$
#
# @author Ton Ngo
include		$(RVM_BUILD)/Make.rules.target

.SUFFIXES:	.c .C .o .h

.C.o:
	gcc -funsigned-bitfields -I. -I$(JAL_ROOT)/include -g -c $<

.c.o:
	gcc -funsigned-bitfields -I. -I$(JAL_ROOT)/include -g -c $<
disasm.o: disasm.C disasm.h
	gcc -funsigned-bitfields -g -c disasm.C

clean:
	rm -f *.o 
