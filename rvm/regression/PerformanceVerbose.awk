#
# (C) Copyright IBM Corp. 2001
#

/^RESULT:/ { 
    if ("x$4" != "x") {
	currentImage = $2.$4; currentBench = $3; 
        print " ";
    } else {
	currentImage = $2; currentBench = $3; 
        print " ";
    }
}

/./ { 
  if (targetBench == currentBench) {
     print;
  }
}
