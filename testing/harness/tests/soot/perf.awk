#
# This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author Stephen Fink

# collect index results for summary line
/Soot has run for/ { sec = 60*$5 + 7; next }

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print "Bottom Line(Seconds): " sec
}
