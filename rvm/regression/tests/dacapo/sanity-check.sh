#!/bin/bash
#
# (C) Copyright Australian National University, 2005
#
# Sanity check a dacapo run by testing the input file against the
# number of benchmarks run
#
# $Id$
#
# @author Robin Garner
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
