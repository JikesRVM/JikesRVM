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

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.runtime.VM_SysCall;
import org.jikesrvm.util.VM_StringUtilities;
import org.vmmagic.unboxed.Address;

/**
 * Platform independent utility functions called from VM_JNIFunctions
 * (cannot be placed in VM_JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see VM_JNIFunctions
 */
public abstract class VM_JNIGenericHelpers {

  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  public static byte[] createByteArrayFromC(Address stringAddress) {

    // disable alignment checking for this method
    if (VM.AlignmentChecking) {
      VM_SysCall.sysCall.sysDisableAlignmentChecking();
    }

    // scan the memory for the null termination of the string
    int length = 0;
    for (Address addr = stringAddress; true; addr = addr.plus(4)) {
      int word = addr.loadInt();
      int byte0, byte1, byte2, byte3;
      if (VM.LittleEndian) {
        byte3 = ((word >> 24) & 0xFF);
        byte2 = ((word >> 16) & 0xFF);
        byte1 = ((word >> 8) & 0xFF);
        byte0 = (word & 0xFF);
      } else {
        byte0 = ((word >> 24) & 0xFF);
        byte1 = ((word >> 16) & 0xFF);
        byte2 = ((word >> 8) & 0xFF);
        byte3 = (word & 0xFF);
      }
      if (byte0 == 0) {
        break;
      }
      length++;
      if (byte1 == 0) {
        break;
      }
      length++;
      if (byte2 == 0) {
        break;
      }
      length++;
      if (byte3 == 0) {
        break;
      }
      length++;
    }

    byte[] contents = new byte[length];
    VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), stringAddress, length);

    // re-enable alignment checking
    if (VM.AlignmentChecking) {
      VM_SysCall.sysCall.sysEnableAlignmentChecking();
    }

    return contents;
  }

  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java String with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  public static String createStringFromC(Address stringAddress) {
    byte[] tmp = createByteArrayFromC(stringAddress);
    if (VM.fullyBooted) {
      return new String(tmp);
    } else {
      // Can't do real Char encoding until VM is fully booted.
      // All Strings encountered during booting must be ascii
      return VM_StringUtilities.asciiBytesToString(tmp);
    }
  }

  /**  A JNI helper function, to set the value pointed to by a C pointer
   * of type (jboolean *).
   * @param boolPtr Native pointer to a jboolean variable to be set.   May be
   *            the NULL pointer, in which case we do nothing.
   * @param val Value to set it to (usually TRUE)
   *
   * XXX There was a strange bug where calling this would crash the VM.
   * That's why it's ifdef'd.  So the dozen-odd places in VM_JNIFunctions
   * where I would use it instead have this code inlined, guarded with an
   * #if.  --Steve Augart
   */

  static void setBoolStar(Address boolPtr, boolean val) {
    // VM.sysWriteln("Someone called setBoolStar");
    if (boolPtr.isZero()) {
      return;
    }
    int temp = boolPtr.loadInt();
    int intval;
    if (VM.LittleEndian) {
      if (val) {
        // set to true.
        intval = (temp & 0xffffff00) | 0x00000001;
      } else {
        // set to false
        intval = (temp & 0xffffff00);
      }
    } else {
      /* Big Endian */
      if (val) {
        // set to true
        intval = (temp & 0x00ffffff) | 0x01000000;
      } else {
        // set to false
        intval = temp & 0x00ffffff;
      }
    }
    boolPtr.store(intval);
  }
}
