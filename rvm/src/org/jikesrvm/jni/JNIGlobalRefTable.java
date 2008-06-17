/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni;

import java.lang.ref.WeakReference;
import org.jikesrvm.VM;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Weak Global References are global references (negative numbers), with the
 * 2^30 bit UNset.  Mask in the 2^30 bit to get the real index into the table.
 */
public class JNIGlobalRefTable {

  @Entrypoint
  private static Object[] refs = new Object[100];
  private static int free = 1;

  static int newGlobalRef(Object referent) {
    if (VM.VerifyAssertions) VM._assert(MM_Interface.validRef(ObjectReference.fromObject(referent)));

    if (free >= refs.length) {
      Object[] newrefs = new Object[refs.length * 2];
      org.jikesrvm.classloader.RVMArray.arraycopy(refs, 0, newrefs, 0, refs.length);
      refs = newrefs;
    }

    refs[free] = referent;
    return -free++;
  }

  /* Weak references are returned with the STRONG_REF_BIT bit UNset.  */
  public static final int STRONG_REF_BIT = 1 << 30;

  static int newWeakRef(Object referent) {
    int gref = newGlobalRef(new WeakReference<Object>(referent));
    return gref & ~STRONG_REF_BIT;
  }

  static void deleteGlobalRef(int index) {
    if (VM.VerifyAssertions) VM._assert(!isWeakRef(index));
    refs[-index] = null;
  }

  static void deleteWeakRef(int index) {
    if (VM.VerifyAssertions) VM._assert(isWeakRef(index));
    int gref = index | STRONG_REF_BIT;
    deleteGlobalRef(gref);
  }

  static Object globalRef(int index) {
    if (VM.VerifyAssertions) VM._assert(!isWeakRef(index));

    return refs[-index];
  }

  static Object weakRef(int index) {
    if (VM.VerifyAssertions) VM._assert(isWeakRef(index));
    @SuppressWarnings("unchecked") // yes, we're being bad.
        WeakReference<Object> ref = (WeakReference<Object>) refs[-(index | STRONG_REF_BIT)];
    return ref.get();
  }

  static Object ref(int index) {
    if (isWeakRef(index)) {
      return weakRef(index);
    } else {
      return globalRef(index);
    }
  }

  static boolean isWeakRef(int index) {
    return (index & STRONG_REF_BIT) == 0;
  }

}
