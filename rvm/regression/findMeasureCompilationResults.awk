#
# (C) Copyright IBM Corp. 2001
#
# $Id$
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

