/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */
class VM_JNIGlobalRefTable {

    static private Object[] refs = new Object[ 100 ];
    static private int free = 1;

    static int newGlobalRef(Object referent) {

	if (VM.VerifyAssertions) VM._assert( VM_Interface.validRef( VM_Magic.objectAsAddress(referent) ) );
	
	if (free >= refs.length) {
	    Object[] newrefs = new Object[ refs.length * 2 ];
	    VM_Array.arraycopy(refs, 0, newrefs, 0, refs.length);
	    refs = newrefs;
	}

	refs[ free ] = referent;
	return - free++;
    }

    static void deleteGlobalRef(int index) {
	refs[ - index ] = null;
    }

    static Object ref(int index) {
	return refs[ - index ];
    }
}
