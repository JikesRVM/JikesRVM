#! /usr/bin/env bash
set -e
msg="Do we have a strtold() function?"
echo $msg >> ${LOG}
echo -n "$msg..." >&2

. ${JAL_BUILD}/environment.target

echo "\
/* HAVE_STRTOLD: Do we have a working strtold() function?  AIX 5.1 
   does not declare strtold() in <stdlib.h> unless 
   sizeof (long double) > sizeof (double).   Note that C '99 requires 
   that function to be present. */"

if ${CC} -Wmissing-prototypes -Wno-unused -Werror -o ${SCRATCH}/have_strtold.o -c have_strtold.c >> ${LOG} 2>&1
then
    echo >&2 "yep"
    echo "#define HAVE_STRTOLD 1"
else
    echo >&2 "no"
    echo "#undef HAVE_STRTOLD"
fi
echo ""
echo ""
