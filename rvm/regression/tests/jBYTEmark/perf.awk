#
# (C) Copyright IBM Corp. 2001
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
