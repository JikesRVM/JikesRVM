JAL_BUILD ?= ${RVM_BUILD}
include			$(JAL_BUILD)/Make.rules.target

CONFIG_H_DIR ?= .
SCRATCH ?= ${CONFIG_H_DIR}/ConfigScratch
CONFIG_H = ${CONFIG_H_DIR}/config.h
LOG = ${CONFIG_H_DIR}/config.log

RUN:=SCRATCH=${SCRATCH} LOG=${LOG} JAL_BUILD=${JAL_BUILD} ${BASH} 

all: ${CONFIG_H}

clean: 
	$(RM) ${CONFIG_H}

${CONFIG_H}: prep
	${RUN} have_strtold.sh > $@

prep:
	mkdir -p ${SCRATCH} ${CONFIG_H_DIR}

.PHONY: all clean prep
