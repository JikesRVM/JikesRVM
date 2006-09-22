#! /bin/bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright © IBM Corp. 2004
#
# $Id$
#
# Install Jikes RVM in /usr (/usr/bin, /usr/lib/jikesrvm,
# /usr/share/jikesrvm, /usr/share/doc/jikesrvm, /usr/man/man1) on a
# system.
#
# @author Steven Augart
# @date 28 April 2004
# 
unset makeflag
unset verbose
[[ $1 = -v ]] && verbose=1
(( verbose )) || makeflag=--quiet
(( verbose )) && echo ./macro-expand.sh GNUmakefile.in  GNUmakefile
./macro-expand.sh GNUmakefile.in  GNUmakefile
make $makeflag -f GNUmakefile install
# To clean up the installation, run: make uninstall


