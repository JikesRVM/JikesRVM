#
# (C) Copyright IBM Corp. 2002
#
# $Id$
#
# @author Michael Hind

# collect index results for summary line
/completed, / { sec = $4 * .001; next }

# ignore everything else
/.*/ { next }

# print summary at the end
END {
    print "Bottom Line(Seconds): " sec
}
