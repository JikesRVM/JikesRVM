#! /bin/bash
# $Id$
# Install Jikes RVM on a Debian system.
unset makeflag
unset verbose
[[ $1 = -v ]] && verbose=1
(( verbose )) || makeflag=--quiet
(( verbose )) && echo ./macro-expand.sh GNUmakefile.in  GNUmakefile
./macro-expand.sh GNUmakefile.in  GNUmakefile
make $makeflag -f GNUmakefile install
# To clean up the installation, run: make uninstall


