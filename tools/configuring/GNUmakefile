# -*- coding: iso-8859-1 ; mode: Makefile ;-*-
#
# Copyright © IBM Corp. 2003
#
# $Id$
#
# This is src/tools/configuring/GNUmakefile.  It does some auto-configuration
# on the target machine.
#
# @author Steven Augart
# @date 20 October 2003

JAL_BUILD ?= ${RVM_BUILD}
ifneq (${JAL_BUILD},"")
  include			$(JAL_BUILD)/Make.rules.target
endif

CONFIG_H_DIR ?= .
SCRATCH ?= ${CONFIG_H_DIR}/ConfigScratch
CONFIG_H := ${CONFIG_H_DIR}/config.h
LOG := ${CONFIG_H_DIR}/config.log
CONFIG_MAKE := ${CONFIG_H_DIR}/Make.config # Unused
CONFIG_SH := ${CONFIG_H_DIR}/config.sh

TARGETS := ${CONFIG_H} ${CONFIG_SH} # ${CONFIG_MAKE} 

## Is the SILENT (-s) flag specified?

ifneq (,$(findstring -s,${MFLAGS}))
# If we are silent:
SILENCE:=2> /dev/null
else
# If we are NOT being silent:
SILENCE:=
endif
RUN:=SCRATCH=${SCRATCH} LOG=${LOG} JAL_BUILD=${JAL_BUILD} ${BASH} 

all: ${TARGETS}

clean: 
#	echo "MFLAGS is ${MFLAGS}"
#	echo "MAKEFLAGS is ${MFLAGS}"
	$(RM) -r ${TARGETS} ${SCRATCH} ${LOG}

${CONFIG_H}: 
	$(MAKE) prep
	@(echo >&2 "Configuring target.  Log in ${LOG},") ${SILENCE}
	@(echo >&2 "  config.h in ${CONFIG_H}, Make.config in ${CONFIG_MAKE}") ${SILENCE}
	@> ${LOG}		# zero out the log file.
	@> $@			# zero out config.h
	@${RUN} have_cxx_strtold.sh		>> $@ ${SILENCE}
## Disabled because we do not use this feature test right now.
#	@${RUN} printf_handles_percent_z.sh	>> $@ ${SILENCE}

# ${CONFIG_MAKE}: prep
# 	@> $@
# ifeq (${RVM_WITH_GCSPY},1)
# 	@${RUN} cxx_with_W_no_endif_labels.sh --make >> $@ ${SILENCE}
# endif

${CONFIG_SH}: 
	$(MAKE) prep
	@> $@
## If you apply the newer GCspy patch, you won't need to invoke
## g++ with -Wno-endif-labels.  Otherwise, it can be useful to do
## so if warnings are enabled.
ifeq (${RVM_WITH_GCSPY},1)
	@${RUN} cxx_with_W_no_endif_labels.sh --sh >> $@ ${SILENCE}
endif

prep:
	mkdir -p ${CONFIG_H_DIR} ${SCRATCH} 

.PHONY: all clean prep
