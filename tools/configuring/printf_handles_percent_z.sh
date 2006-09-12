#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# (C) Copyright © IBM Corp. 2003, 2005
#
# $Id$
#
# Purpose: Autodetect whether:
msg="Does printf() understand the '%z' modifier?"
#
# @author Steven Augart
# @date 20 October 2003

set -e
echo "$msg" >> ${LOG}
echo >&2 -n "$msg..."

. ${JAL_BUILD}/environment.target

echo "\
/* PRINTF_HANDLES_PERCENT_Z: Does printf() know about the C 99 '%z' 
   modifier, for indicating that we're printing a size_t?
   AIX 5.1's printf() doesn't. 

   This test is less useful than it might be, because it's a hassle to
   write a portable way around needing %z; less work is to substitute
   %lu for %zu, and then cast the size_t argument to (unsigned long).
   So this test is disabled, although it could be brought on-line again
   if you ever decided to use it.
 */"
CMD=printf_handles_percent_z

${CC} -w -o ${SCRATCH}/${CMD} ${CMD}.c 2>&1 >> ${LOG} 

${SCRATCH}/${CMD} > ${SCRATCH}/${CMD}.out
if grep z ${SCRATCH}/${CMD}.out 2>&1 >> $LOG; then
    echo "$msg...No" >> ${LOG}
    echo No >&2
    echo "#undef PRINTF_HANDLES_PERCENT_Z"
else
    echo "$msg...Yes" >> ${LOG} 
    echo Yes >&2
    echo "#define PRINTF_HANDLES_PERCENT_Z 1"
fi
echo ""
echo ""
