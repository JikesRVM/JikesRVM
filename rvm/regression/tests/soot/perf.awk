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
