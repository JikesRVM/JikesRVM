#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
#
# Find the measure compilation output.
#
# @author Peter Sweeney
# @date 2/7/2002
BEGIN {
    yes = "yes"
    no = "no"
    skipAll=yes
}

#
# print everything after found start of report
#
/.*/ && skipAll==no {print}

#
# looking for start of report
#
/Compilation Subsystem Report/ { skipAll=no; print }

#
# skip everything until find start of report
#
/.*/ && skipAll==yes {next}

