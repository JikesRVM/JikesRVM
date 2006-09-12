#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# (C) Copyright © IBM Corp. 2004

# This is part of Jikes RVM, which is free software, 
# distributed under the Common Public License.

# $Id$

# Make a fake Sun-style Java Development Kit directory.

# @author Steven Augart
# @date 22 April 2004, 24 April 2004, 

# Details:
# Make a fake Sun-style Java Development Kit directory in $1
# It is OK for $1 to be a subdirectory of $RVM_BUILD.
#
# Run this on either the host or the target.  Defaults to the target, 
# since we've presumably already build the "JikesRVM" launcher executable,
# which we will be exploiting here.

# I was inspired by Kaffe's emulation of a Sun-style JDK.
# This work is in no other respect to be considered a derivative work
# of Kaffe.

cat >&2 <<EOF
XXX This code is a work-in-progress.
XXX
XXX I have temporarily put it aside for other work.
XXX
XXX It is in CVS so that I can conveniently share it between my laptop and
XXX my desktop workstation.
XXX
XXX It may not even contain valid Bash syntax.
EOF

## Destination directories:
declare -r jdk="$1"
declare -r jre=${jdk}/jre
declare -r hardware_platform=$(uname -i)
declare -r nativelib=${jre}/lib/$hardware_platform


## Source directories:
export src=${RVM_SOURCE-$RVM_ROOT/rvm}
export bld=$RVM_BUILD

. $bld/environment.target

# Temporary working directory.
tmpdir=/tmp/make-emulated-sunjdk.$$
trap 'rm -rf $tmpdir' EXIT
mkdir -p $tmpdir

## Build root JDK directory.
mkdir -p "$jdk" || die
cd $jdk
prefix=$jdk
exec_prefix=$jdk
install -d bin include jre lib man share jre/bin jre/lib jre/plugin

## Copy the top-level docs.
cp  $src/LICENSE .
cat $src/ReleaseNotes-*[^~] > jre/CHANGES

## Manual pages and other docs.
cp -r $src/doc/man/ man/
cp -r $src/doc/emulated-jdk/man/ man/

## Source code
## XXX TODO: Include the GNU Classpath source as well.
cp $bld/RVM.classes/jksvmsrc.jar src.zip

## Now copy the binaries.

[[ $FASTJAR ]] && cat > bin/jar << EOF
#! /bin/sh
exec $FASTJAR "$@"
EOF

cd $jdk
cat >> bin/java <<EOF
#! /bin/sh
# "java" emulation wrapper for Jikes RVM.
prefix=\${JAVA_HOME-${prefix}}
exec ${prefix}/jre/bin/rvm "$@"
EOF

cp bin/java $jre/bin


cat >> bin/javac <<EOF
# "javac" emulation wrapper for Jikes RVM.
prefix=\${JAVA_HOME-${prefix}}
jrelib=${prefix}/jre/lib
exec ${JIKES} -bootclasspath ${jrelib}/rt.jar "$@"
EOF

## Libraries
install -d jre/lib
install -d jre/lib/ext
install -d $nativelib

## Native items to the libraries
# The boot image loader
cp $bld/JikesRVM $nativelib/JikesRVM-boot-image-loader

# Other native items.
cp $bld/*.so $nativelib

## General items.
## Create jre/lib/rt.jar
cd $tmpdir
rm -rf *
jar -xf $bld/RVM.classes/rvmrt.jar
jar -xf $bld/RVM.classes/jksvm.jar
jar -cf $jre/lib/rt.jar *
rm -rf *
# and return to base.
cd $jdk

install -d jre/lib/security
cp $bld/JikesRVM.security jre/lib/security/java.security

## 
## LATER
##

## Permissions
chmod +x bin/* jre/bin/*
