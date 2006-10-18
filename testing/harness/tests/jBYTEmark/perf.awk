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
# @author Julian Dolby
#

# collect index results for summary line
/Integer Index/ { intPerf = $3; next }
/FP Index/ { fpPerf = $3; next }

# pass thru individual results
/^[ A-Za-z]*:[^A-Za-z]*$/ { print $0 }

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print "Bottom Line: Integer Index " intPerf ", FP Index ", fpPerf;
}
