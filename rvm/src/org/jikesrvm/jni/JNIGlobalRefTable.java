/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni;

import java.lang.ref.WeakReference;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Address;

/**
 * Weak Global References are global references (negative numbers), with the
 * 2^30 bit UNset.  Mask in the 2^30 bit to get the real index into the table.
 */
public class JNIGlobalRefTable {

  @Entrypoint
  public static AddressArray JNIGlobalRefs = AddressArray.create(100);
  private static int free = 1;

  static int newGlobalRef(Object referent) {
    if (VM.VerifyAssertions) VM._assert(MemoryManager.validRef(ObjectReference.fromObject(referent)));

    if (free >= JNIGlobalRefs.length()) {
      AddressArray newGlobalRefs = AddressArray.create(JNIGlobalRefs.length() * 2);
      copyAndReplaceGlobalRefs(newGlobalRefs);
    }

    JNIGlobalRefs.set(free, Magic.objectAsAddress(referent));
    return -free++;
  }

  @Uninterruptible
  private static void copyAndReplaceGlobalRefs(AddressArray newGlobalRefs) {
    for(int i=0; i < JNIGlobalRefs.length(); i++) {
      newGlobalRefs.set(i, JNIGlobalRefs.get(i));
    }
    JNIGlobalRefs = newGlobalRefs;
  }

  /* Weak references are returned with the STRONG_REF_BIT bit UNset.  */
  public static final int STRONG_REF_BIT = 1 << 30;

  static int newWeakRef(Object referent) {
    int gref = newGlobalRef(new WeakReference<Object>(referent));
    return gref & ~STRONG_REF_BIT;
  }

  static void deleteGlobalRef(int index) {
    if (VM.VerifyAssertions) VM._assert(!isWeakRef(index));
    JNIGlobalRefs.set(-index, Address.zero());
  }

  static void deleteWeakRef(int index) {
    if (VM.VerifyAssertions) VM._assert(isWeakRef(index));
    int gref = index | STRONG_REF_BIT;
    deleteGlobalRef(gref);
  }

  @Uninterruptible
  static Object globalRef(int index) {
    if (VM.VerifyAssertions) VM._assert(!isWeakRef(index));

    return Magic.addressAsObject(JNIGlobalRefs.get(-index));
  }

  @Uninterruptible
  static Object weakRef(int index) {
    if (VM.VerifyAssertions) VM._assert(isWeakRef(index));
    @SuppressWarnings("unchecked") // yes, we're being bad.
    WeakReference<Object> ref = (WeakReference<Object>) globalRef(index | STRONG_REF_BIT);
    return java.lang.ref.JikesRVMSupport.uninterruptibleReferenceGet(ref);
  }

  @Uninterruptible
  static Object ref(int index) {
    if (isWeakRef(index)) {
      return weakRef(index);
    } else {
      return globalRef(index);
    }
  }

  @Uninterruptible
  static boolean isWeakRef(int index) {
    return (index & STRONG_REF_BIT) == 0;
  }
}
