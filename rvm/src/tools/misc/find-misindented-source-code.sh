#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# (C) Copyright © IBM Corp. 2003
#
# $Id$
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
