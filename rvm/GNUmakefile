#
# (C) Copyright IBM Corp. 2003
#
# $Id$
#
# @author Steven Augart 
#
.PHONY: all
all:
	@echo >&2 "This is not the makefile for building JikesRVM.  It is"
	@echo >&2 " just for building a TAGS table for GNU Emacs.  Type"
	@echo >&2 ' "make TAGS" to use it.'
	@echo >&2 ' (Well, it can also run "find" in ways useful for the Emacs Meta-x grep-find '
	@echo >&2 ' command.  It also makes an alternative tags file, TAGS.ALL, which contains'
	@echo >&2 ' all files in the source tree, skipping only Emacs backups and CVS files.)'


FIND:=find . -name CVS -prune -o \( -name \*.java -o -name \*.C -o -name \*.h -o -name \*.c \)
FINDALL:=find . -name CVS -prune -o -name \*~ -o -name .\*~ -o -name '.#*' -o -name '#*' -o -name '*#' -o -type f 

.PHONY: FORCE
TAGS: FORCE
	$(RM) $@
	$(FIND) -print0 | xargs -0 etags --append --output=$@

FORCE:

.PHONY: find0 find findall findall0
find0: FORCE
	@$(FIND) -print0

find: FORCE
	@$(FIND) -print

findall0: FORCE
	@$(FINDALL) -print0

findall: FORCE
	@$(FINDALL) -print

TAGS.ALL: FORCE
	$(RM) $@
	$(FINDALL) -print0 | xargs -0 etags --append --output=$@
