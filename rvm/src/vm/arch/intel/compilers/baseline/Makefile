#
# (C) Copyright IBM Corp. 2001
#
#$Id$

# @author Bowen Alpern

include		$(RVM_ROOT)/Make.rules

default: 	test

test:		Test.class VM_Lister.class VM_Compiler.class
		jsh java VM_Lister Test

new:		clean default

compile:	VM_Compiler.class VM_Lister.class
		jsh java VM_Lister VM_Compiler

clean:;		$(RM) *.class *~
