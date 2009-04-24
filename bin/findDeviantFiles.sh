#!/bin/bash
#
#  This file is part of the Jikes RVM project (http://jikesrvm.org).
#
#  This file is licensed to You under the Eclipse Public License (EPL);
#  You may not use this file except in compliance with the License. You
#  may obtain a copy of the License at
#
#      http://www.opensource.org/licenses/eclipse-1.0.php
#
#  See the COPYRIGHT.txt file distributed with this work for information
#  regarding copyright ownership.
#

export BIN_DIR="`dirname "$0"`"

export FILTER="awk -f $BIN_DIR/headerExceptionsFilter.awk"

echo "Missing License Header"
find . -name .svn -prune -o -name generated -prune -o -name target -prune -o -name dist -prune -o -name results -prune -o -type f ! -exec grep -L "This file is licensed to You under the Common Public License" {} \; | $FILTER  
