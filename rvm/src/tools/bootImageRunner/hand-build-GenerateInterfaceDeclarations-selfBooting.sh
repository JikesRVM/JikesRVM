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

# $JAL_BUILD contains the .so files, thus the argument
# -nativeLibDir $JAL_BUILD
$HOST_JAVA_RT -Xmx200M	  com.ibm.JikesRVM.GenerateInterfaceDeclarations.GenerateInterfaceDeclarations  -alternateRealityClasspath .:$JAL_BUILD/RVM.classes:$JAL_BUILD/RVM.classes/rvmrt.jar -alternateRealityNativeLibDir $JAL_BUILD -out declarations.out -ia 0x43000000
# >	  $JAL_BUILD/RVM.scratch/InterfaceDeclarations.h.new
