#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

/^RESULT:/ { 
    if ("x$4" != "x") {
	currentImage = $2.$4; currentBench = $3; 
        print " ";
    } else {
	currentImage = $2; currentBench = $3; 
        print " ";
    }
}

/./ { 
  if (targetBench == currentBench) {
     print;
  }
}
