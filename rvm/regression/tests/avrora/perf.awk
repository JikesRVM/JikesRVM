#
# (C) Copyright IBM Corp. 2005
#
# $Id$
#
# @author Michael Hind

# collect index results for summary line
/Time for simulation/ { sec = $4; next }

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print "Bottom Line(Seconds): " sec
}
