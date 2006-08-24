#! /usr/bin/awk -f
## -*- coding: iso-8859-1; -*-
##
## allkids.awk
## Copyright © IBM Corp. 2003
##
## $Id$
##
## Parse the output of "ps -ef" so that we have an array that records the
## parent/child relationships of all processes in the "ps" output.  Use
## that information to determine which processes are descendants of the
## command-line arguments. 
##
## @author Steven Augart
## @date 11 October 2003

BEGIN {
    ## Every argument process ID # gets put on death row.
    for (i = 1; i < ARGC; ++i) {
        pid=ARGV[i];
        death_row[pid] = pid;
    }
    ARGC=1                      # don't treat ARGC as containing arguments.
}

## Now read in each line of the output of "ps".  The second field is the
## process ID of the process in question (PID), and the third field is the PID
## of its parent (PPID).  (We could parse this out of the PS output header,
## but we do not need to do so right now.)
{
    kid=$2;
    parent=$3;
    parent_of[kid] = parent;
    ## Do an initial pass: if my parent is on death row, then I should be too.
    if (parent in death_row)
        death_row[kid] = kid;
}

END {
    ## Make sure we got everyone into death row who's a (recusive) descendant
    ## of the original PIDs on death row.
    ## 
    ## Loop until a pass happens where we haven't added anyone else to
    ## death row.
    do {
        added_one=0;              # we haven't added anyone to death_row yet.
        for (kid in parent_of) {
            if (parent_of[kid] in death_row) {
                if (! (kid in death_row) ) {
                    ++added_one;
                    death_row[kid] = kid;
                }
            }
        }
    } while (added_one);

    ## Now print out the numbers of all of the death row inmates.
    ## (Can you tell that this code was written by an American?)
    for (inmate in death_row) {
        print inmate;
    }
}

