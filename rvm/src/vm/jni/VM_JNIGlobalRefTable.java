/*
 * (C) Copyright IBM Corp. 2002, 2004
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.*;
import java.lang.ref.WeakReference;

import org.vmmagic.unboxed.*;

/**
 * @author Julian Dolby
 * @date May 20, 2002
 *
 * @modified Steven Augart -- to handle Weak References as well. 
 *
 * Weak Global References are global references (negative numbers), with the
 * 2^30 bit UNset.  Mask in the 2^30 bit to get the real index into the table.
 */
class VM_JNIGlobalRefTable {

  static private Object[] refs = new Object[ 100 ];
  static private int free = 1;

  static int newGlobalRef(Object referent) {
    if (VM.VerifyAssertions) VM._assert(MM_Interface.validRef(ObjectReference.fromObject(referent)));
        
    if (free >= refs.length) {
      Object[] newrefs = new Object[ refs.length * 2 ];
      com.ibm.JikesRVM.classloader.VM_Array.arraycopy(refs, 0, newrefs, 0, refs.length);
      refs = newrefs;
    }

    refs[ free ] = referent;
    return - free++;
  }

  /* Weak references are returned with the STRONG_REF_BIT bit UNset.  */
  static final int STRONG_REF_BIT = 1 << 30;

  static int newWeakRef(Object referent) {
    int gref = newGlobalRef(new WeakReference(referent));
    return gref & ~STRONG_REF_BIT;
  }

  static void deleteGlobalRef(int index) {
    if (VM.VerifyAssertions) VM._assert( ! isWeakRef(index));
    refs[ - index ] = null;
  }

  static void deleteWeakRef(int index) {
    if (VM.VerifyAssertions) VM._assert( isWeakRef(index));
    int gref = index | STRONG_REF_BIT;
    deleteGlobalRef(gref);
  }

  static Object globalRef(int index) {
    if (VM.VerifyAssertions) VM._assert(! isWeakRef(index));
    
    return refs[ - index ];
  }

  static Object weakRef(int index) {
    if (VM.VerifyAssertions) VM._assert( isWeakRef(index));
    WeakReference ref = (WeakReference) refs[ - (index | STRONG_REF_BIT)];
    return ref.get();
  }

  static Object ref(int index) {
    if (isWeakRef(index))
      return weakRef(index);
    else
      return globalRef(index);
  }

  static boolean isWeakRef(int index) {
    return ( index & STRONG_REF_BIT ) == 0;
  }

}
