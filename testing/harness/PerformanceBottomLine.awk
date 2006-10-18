#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

/^RESULT:/ { 
    if ("x$4" != "x") {
	currentImage = $2.$4; currentBench = $3; 
    } else {
	currentImage = $2; currentBench = $3; 
    }
}

/^Bottom Line: / { results[ currentBench, currentImage ] = substr($0, 13) }

END {    
    for (result in results ) {
        split(result, parts, SUBSEP);
        benches[ parts[1] ] = "YES";
    }
       
    for (bench in benches) {
	print "\nResults for " bench;
	print "----------------------------------";

	for (result in results) {
	    split(result, parts, SUBSEP);
	    rbench=parts[1];
	    rimage=parts[2];
	    
	    if ( rbench == bench ) {
		printf "%-25s%-50s\n", rimage, results[ result ];
	    }
	}
    }
}
