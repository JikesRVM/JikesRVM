#
# (C) Copyright IBM Corp. 2003
#
# $Id$
#
# @author Steven Augart 
#

## This Makefile uses Emacs 21's "etags" program to build its tags table.
## This is installed by default on Red Hat Linux as "etags", but on 
## Debian exuberant-ctags is the default etags -- you can change this by 
## running, as root:
##   ``update-alternatives --config etags'' 

ETAGS_EMACS:=etags
## For Debian:
# ETAGS_EMACS=etags.emacs21

# Here are the most useful targets in this makefile:
#
# "make TAGS": * Make a tags table, "TAGS", for the generated
#	  sources (in $RVM_BUILD) and for the C, C++, and Java sources
#	   in the 'src' directory.  
#	* Skip 'src/examples'
#	* The table should include the Java templates in *.template
#
# "make TAGS.ALL": Make a tags table covering all of the source files
#	 in the 'rvm' tree and all of the generated files.  
#
# "make TAGS.Makefiles": Make a tags table covering all of the Makefiles.
#
# "make conditionals": Search the sources for any .java files 
#	containing preprocessor conditionals.  Filter out the conditional
#	expressions.  Dump the sorted list of unique conditional variables
#	to stdout.
#
# "make -s findall0": Search through the rvm source tree and the generated
#	source tree.  Print all file names, separated by ASCII NUL 
#	characters.  This makes output useful for the GNU "xargs -0" command 
#	to use.


.PHONY: all
all:
	@echo >&2 "This is not the makefile for building JikesRVM.  It is"
	@echo >&2 " just for building a TAGS table for GNU Emacs.  Type"
	@echo >&2 ' "make TAGS" to use it.'
	@echo >&2 ' (Well, it can also run "find" in ways useful for the Emacs Meta-x grep-find '
	@echo >&2 ' command.  It also makes an alternative tags file, TAGS.ALL, which contains'
	@echo >&2 ' all files in the source tree, skipping only Emacs backups and CVS files.)'
	@echo >&2 ' Documentation is at the head of the Makefile.'


## If RVM_BUILD is in the environment, then we have a real Jikes RVM build that
## we can search for the generated sources (the ones that don't appear here).
ifdef RVM_BUILD
   GENSRC:="$(RVM_BUILD)/RVM.generatedSources"
else
   GENSRC:=
endif
XFLAGS:=--no-run-if-empty

find_ignore_docs:=-name \*.gif -o -name \*.jpg -o -name \*.ps -o -name \*.dvi -o -name \*.ind -o -name \*.ilg
find_match_junk_names:=-name Old -prune -o -name '*.[ao]' -prune -o -name \*.so -prune -o -name CVS -prune -o -name TAGS\* -o -name \*~ -o -name .\*~ -o -name '.\#*' -o -name '\#*' -o -name '*\#' 
append_java_templates=find src ../MMTk -follow ${find_match_junk_names}  -o -name examples -prune -o -name \*.template -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --language=java --append --output=$@ 

# Throwing ${find_match_junk_names} into ${FIND} doesn't cut down on the size
# in a clean checkout, but might possibly do so if there were old 
# files left over whose names start with "#" or with ".#". 
FIND:=find  $(GENSRC) src ../MMTk/src -follow  ${find_match_junk_names} -o -name examples -prune -o \( -name \*.java -o -name \*.C -o -name \*.h -o -name \*.c -o -name \*.y \)
FINDALL:=find $(GENSRC) . ../MMTk -follow ${find_match_junk_names} -o ${find_ignore_docs} -o -type f 

# etags in emacs 21.3.CVS-2003-09-23 does not support "--defines, nor  --globals".
EFLAGS:=--members --declarations --language=java
## Exuberant CTAGS 5.5.4 doesn't support these options; you'll have to modify
## this program to use "Exuberant CTAGS"
# EFLAGS:=

# A forced target.
.PHONY: FORCE tags
FORCE:

tags: TAGS			# Convenient alias.

TAGS: FORCE
	$(RM) $@
	$(FIND) -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --append --output=$@
ifdef RVM_BUILD
	find  "$(RVM_BUILD)/RVM.scratch" \( -name \*.C -o -name \*.java -o -name \*.h -o -name \*.c -o -name \*.y \) -type f -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --append --output=$@
endif
	${append_java_templates}

TAGS.ALL: FORCE
	$(RM) $@
	$(FINDALL) ! -name \*.template -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --append --output=$@
ifdef RVM_BUILD
	find  "$(RVM_BUILD)/RVM.scratch" \( -name \*.C -o -name \*.java -o -name \*.h -o -name \*.c -o -name \*.y \) -type f -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --append --output=$@
endif
	${append_java_templates}

TAGS.Makefiles: FORCE
	$(RM) $@
	find . ../MMTk -follow ${find_match_junk_names} -o -name regression -prune -o -name examples -prune -o -name '*akefil*' -print0 | xargs ${XFLAGS} -0 $(ETAGS_EMACS) $(EFLAGS) --append --output=$@

## We don't have any conditional compilation directives in the 
## MMTk source tree; no need to search it.
.PHONY: conditionals
# Note: explicit tab characters below.
conditionals:
	find . -follow -name \*.java -print0 | xargs ${XFLAGS} -0 fgrep --no-filename '//-#' | sed -e 's/.*#[a-z]\+[ 	]*//' | sed  -e '/\/\/.*/d' | tr '[!&|]' '\n' |sed  -e 's/[ 	]*$$//' -e 's/^[ 	]//'  -e '/^$$/d'  | sort | uniq
# | cat -v -e -t


## These targets may be handy if you're writing code that wants to do
## a find-like hunt.  The main problem is they won't be able to tell
## '$(ETAGS_EMACS)' that the .template files contain Java code. 

.PHONY: find0 find findall findall0
find0: FORCE
	$(FIND) -print0

find: FORCE
	$(FIND) -print

findall0: FORCE
	$(FINDALL) -print0

findall: FORCE
	$(FINDALL) -print

