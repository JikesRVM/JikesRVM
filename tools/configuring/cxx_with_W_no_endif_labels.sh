#! /usr/bin/env bash
set -e
msg="Does C++ support -Wno-endif-labels (for GCSPY)?"
varname=CPLUS_NO_ENDIF_LABELS_WARNING
echo $msg >> ${LOG}
echo -n "$msg..." >&2
. ${JAL_BUILD}/environment.target

# Header comment for the file.
echo "\
# ${msg}
## Not all versions of g++ will have this flag available.  Moreover, we
## might have \$CPLUS not run g++"

echo -n $varname
if [[ $1 = --make ]]; then
    echo -n " := "
elif [[ $1 == --sh ]]; then
    echo -n "="
else
    echo >&2 "WARNING: Usage: $0 { --make | --sh }"
    exit 1
fi

## TODO: Avoid doing this work if someone is building with warnings
## turned off.

RUN_ME="${CPLUS} -o ${SCRATCH}/dummy.o -Wno-endif-labels -c dummy.C" 
echo $RUN_ME >> ${LOG}
if $RUN_ME >> ${LOG} 2>&1
then
    echo >&2 Yes
    echo "$msg...Yes." >> ${LOG}
    echo "-Wno-endif-labels"
else
    echo "$msg...No" >> ${LOG}
    echo >&2 No
    echo ""
fi
echo ""
echo ""
