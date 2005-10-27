#!/bin/bash
#
# Sanity check a dacapo run by testing the input file against the
# number of benchmarks run
#

RESULTFILE=$1
shift
NTESTS=$#

if [ $( grep -E "===== DaCapo .* Finished in " < $RESULTFILE | wc -l ) == $NTESTS ]
then
  exit 0
else
  exit 1
fi
