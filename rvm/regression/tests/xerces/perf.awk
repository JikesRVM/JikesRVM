#
# (C) Copyright IBM Corp. 2001
#
#
# $Id$
#
# @author Julian Dolby
#

# collect the last reported elapesed time
/ELAPSED TIME/ { eTime = $3; next }

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print "Elasped Time(ms): " eTime
}
