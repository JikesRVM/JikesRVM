#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright ? IBM Corp. 2003
#
#
# Simple program that hunts for files that don't meet our 
# four-space indentation criterion.
#
# @author Steven Augart
# @date 21 November 2003

cd $RVM_ROOT/rvm
find . -name \*.sh | xargs egrep '^(    )*  ? ?[^ ]'
echo "This output will be less useful; false positives inside comments:"
find . -name \*.{C,java,c,h} | xargs egrep '^(    )*  ? ?[^ ]'
