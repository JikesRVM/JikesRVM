/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */
import com.ibm.JikesRVM.memoryManagers.VM_GCUtil;

class VM_JNIGlobalRefTable {

    static Object[] refs = new Object[ 100 ];
    static int free = 0;

    static int newGlobalRef(Object referent) {
	if (VM.VerifyAssertions) VM.assert( free < 100 );
	if (VM.VerifyAssertions) VM.assert( VM_GCUtil.validRef( VM_Magic.objectAsAddress(referent) ) );
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

    
