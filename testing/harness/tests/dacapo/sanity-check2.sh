#!/bin/bash
#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
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

if [ $( grep -E "===== DaCapo .* PASSED in " < $RESULTFILE | wc -l ) == $NTESTS ]
then
  exit 0
else
  exit 1
fi
