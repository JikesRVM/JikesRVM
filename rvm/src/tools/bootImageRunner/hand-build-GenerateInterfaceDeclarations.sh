#! /usr/bin/env bash
#
# (C) Copyright IBM Corp. 2001, 2003
#
# $Id$
#
# Hand-build and run the program GenerateInterfaceDeclarations.java.
# This is useful if you are modifying that program and want a quicker 
# turn-around than building all of Jikes RVM gives you.
#
# @author Steven Augart
# @date 24 September 2003

set -e
. $RVM_BUILD/environment.host

TMP=./tmp
mkdir -p $TMP || :
rm -f $TMP/GenerateInterfaceDeclarations.java
$RVM_BUILD/jbuild.toolPrep --disable-modification-exit-status $TMP GenerateInterfaceDeclarations.java

cd $TMP

rm -f *.class
$RVM_BUILD/jbuild.tool GenerateInterfaceDeclarations.java

$HOST_JAVA_RT -Xmx200M	  -classpath .:$JAL_BUILD/RVM.classes:$JAL_BUILD/RVM.classes/rvmrt.jar	  GenerateInterfaceDeclarations -out declarations.out -ia 0x43000000
# >	  $JAL_BUILD/RVM.scratch/InterfaceDeclarations.h.new
