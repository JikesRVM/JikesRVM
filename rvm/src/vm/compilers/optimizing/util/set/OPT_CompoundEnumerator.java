/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.util.*;

/** 
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_CompoundEnumerator implements Enumeration {
    private final Enumeration first;    
    private final Enumeration second;

    OPT_CompoundEnumerator(Enumeration first, Enumeration second) {
	this.first = first;
	this.second = second;
    }

    public boolean hasMoreElements() {
	return first.hasMoreElements()||second.hasMoreElements();
    }

    public Object nextElement() {
	if (first.hasMoreElements())
	    return first.nextElement();
	else
	    return second.nextElement();
    }
}
