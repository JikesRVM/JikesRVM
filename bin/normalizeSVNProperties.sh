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

# This is really an administrative script used by the core team to maintain
# the svn repository.  It's job is to normalize the svn properties on files
# based on their extensions.
#

# Source code files should have the following properties set:
#   svn:eol-style : native
#   svn:mime-type : text/plain
for extension in .java .c .h .C .jj; do
    find . -name "*$extension" -print | xargs svn propset svn:eol-style native
    find . -name "*$extension" -print | xargs svn propset svn:mime-type text/plain
done




