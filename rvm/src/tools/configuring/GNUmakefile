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
ifneq ($JAL_BUILD,"")
  include			$(JAL_BUILD)/Make.rules.target
endif

CONFIG_H_DIR ?= .
SCRATCH ?= ${CONFIG_H_DIR}/ConfigScratch
CONFIG_H = ${CONFIG_H_DIR}/config.h
LOG = ${CONFIG_H_DIR}/config.log

RUN:=SCRATCH=${SCRATCH} LOG=${LOG} JAL_BUILD=${JAL_BUILD} ${BASH} 

all: ${CONFIG_H}

clean: 
	$(RM) -r ${CONFIG_H} ${SCRATCH} ${LOG}

${CONFIG_H}: prep
	@> ${LOG}		# zero out the log file.
	@> $@			# zero out config.h
	@${RUN} have_strtold.sh >> $@
	@${RUN} printf_handles_percent_z.sh >> $@

prep:
	mkdir -p ${SCRATCH} ${CONFIG_H_DIR}

.PHONY: all clean prep
