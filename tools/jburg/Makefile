# -*-Makefile-*-
## EXPERIMENTAL makefile for jburg.
## Normally run from inside $(SCRATCH_DIR).
## Caller sets VPATH if needed.

## Copyright © IBM Corporation, 2003
## Or, for compatibility with rvm/bin/findDeviantFiles: 
# (C) Copyright IBM Corp. 2003
#
# @author Steven Augart
##
## $Id$
##


ifdef VPATH
    CPPFLAGS+=-I$(VPATH)
endif

include		$(RVM_BUILD)/Make.rules.host

## -Wno-undef: Do not warn if an unknown identifier is evaluated within a #if.
## This is because bison.simple, at least on some machines, tests against the
## following undefined conditionals: YYLSP_NEEDED 
## YYSTACK_USE_ALLOCA YYPURE YYMAXDEPTH 
CFLAGS+=-W -Wall -Wbad-function-cast -Wcast-align -Wpointer-arith -Wcast-qual -Wshadow -Wmissing-prototypes -Wmissing-declarations -fkeep-static-consts  -fkeep-inline-functions -Wwrite-strings -Wno-aggregate-return -Wmissing-noreturn -Wnested-externs -Wtrigraphs -Wconversion -Wsign-compare -Wno-unused -Wno-strict-prototypes -Wno-undef

## For GCC version 3.3, we can add:
## but we don't have the infrastructure to do this programmatically right now.
## -Wfloat-equal -Wendif-labels -Wmissing-format-attribute -Wunreachable-code -Wno-long-long

YFLAGS=

tool:	jburg

## We need to explicitly make the temporary files (the .o files) so that 
## we can use the $^ Make target.
jburg.o gram.o: jburg.h

jburg:	jburg.o gram.o
#	(cd $(SCRATCH_DIR) && (export TMPDIR=.; $(MAKE) gram.c; $(CC) -o jburg jburg.c gram.c))
#	$(MAKE) TMPDIR=. -C $(SCRATCH_DIR) gram.c
#	$(CC) -o $(SCRATCH_DIR)/jburg $(SCRATCH_DIR)/jburg.c $(SCRATCH_DIR)/gram.c
#	$(CC) -o $@ jburg.c gram.c
	$(CC) -o $@ $^


## This is necessary because there is an ugly little bug in GNU Make 3.80
## where the rule to run YACC is broken.
##
## It also modifies the error #file info for us that is written 
## into y.tab.c, so that we do not face the same problem..

gram.c: gram.y
ifdef BISON
	$(BISON) --output=$@ $<
else
	$(YACC) $<
	$(RM) $@
	sed -e 's/"y\.tab\.c"/"gram.c"/' y.tab.c > $@
	$(RM) y.tab.c
	chmod -w $@
endif

clean:
	$(RM) gram.c *.o jburg OPT_BURS_*.java BURS_State.template y.tab.c

.PHONY:	tool clean
