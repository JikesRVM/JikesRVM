#!/bin/bash
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Common Public License (CPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/cpl1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

export BIN_DIR="`dirname "$0"`"

export MATCH="$BIN_DIR/findMatchedFiles.pl"
export FILTER="awk -f $BIN_DIR/headerExceptionsFilter.awk"

echo "Missing Copyright"
find . -name .svn -prune -o -name generated -prune -o -name target -prune -o -name dist -prune -o -name results -prune -o -type f ! -name '.project' ! -name '.classpath' ! -name 'build.xml' -exec egrep "\([cC]\) Copyright|Copyright ?|Copyright \([cC]\)" {} \; -print 

# find . -name .svn -prune -o -name generated -prune -o -name target -prune -o -name dist -prune -o -name results -prune -o -type f ! -name '.project' ! -name '.classpath' ! -print -exec egrep -c "This file is licensed to You under the Common Public License \(CPL\)" {} \; | $MATCH | $FILTER
