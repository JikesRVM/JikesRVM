#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Stephen Fink
#

# collect the total time, throwing out the first iteration
BEGIN {
  iter = 0
  eTime = 0
}

/Method:/ {
  method = $2
}

/ELAPSED TIME/ { 
   if (iter > 0)  {
     eTime = eTime + $3; 
   }
   iter = iter + 1
   next 
}

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print method " Elapsed Time(ms): " eTime
}
