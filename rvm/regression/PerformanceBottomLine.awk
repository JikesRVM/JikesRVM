
/^RESULT:/ { 
    if ("x$4" != "x") {
	currentImage = $2.$4; currentBench = $3; 
    } else {
	currentImage = $2; currentBench = $3; 
    }
}

/^Bottom Line: / { results[ currentBench, currentImage ] = substr($0, 13) }

END {
    lastbench="fake";
    for (result in results ) {
        split(result, parts, SUBSEP);
        bench=parts[1];
	image=parts[2];
	if ( bench != lastbench ) {
	    lastbench = bench;
	    print "Results for " bench;
	}

	print "\t" image "\t" results[ result ];
    }
}
