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
    print "Elasped Time(ms): " eTime
}
