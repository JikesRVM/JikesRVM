#! /usr/bin/env bash
set -e
msg="Does printf() understand the '%z' modifier?"
echo "Testing: $msg" >> ${LOG}
echo >&2 -n "$msg..."

. ${JAL_BUILD}/environment.target

echo "\
/* PRINTF_HANDLES_PERCENT_Z: Does printf() know about the C 99 '%z' 
   modifier, for indicating that we're printing a size_t?
   AIX 5.1's printf() doesn't. */"
CMD=printf_handles_percent_z

${CC} -w -o ${SCRATCH}/${CMD} ${CMD}.c 2>&1 >> ${LOG} 

${SCRATCH}/${CMD} > ${SCRATCH}/${CMD}.out
if grep z ${SCRATCH}/${CMD}.out 2>&1 >> $LOG; then
    echo "no" | tee -a ${LOG} >&2
    echo "#undef PRINTF_HANDLES_PERCENT_Z"
else
    echo "yep" | tee -a ${LOG} >&2
    echo "#define PRINTF_HANDLES_PERCENT_Z 1"
fi
echo ""
echo ""
