
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
