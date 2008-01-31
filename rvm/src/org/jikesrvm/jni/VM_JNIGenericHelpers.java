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
import static org.jikesrvm.VM_SizeConstants.BYTES_IN_ADDRESS;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.runtime.VM_SysCall;
import org.jikesrvm.util.VM_StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Platform independent utility functions called from VM_JNIFunctions
 * (cannot be placed in VM_JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see VM_JNIFunctions
 */
public abstract class VM_JNIGenericHelpers {

  /**
   * Compute the length of the given null-terminated string
   *
   * @param ptr address of string in memory
   * @return the length of the string in bytes
   */
  public static int strlen(Address ptr) {
    int length=0;
    // align address to size of machine
    while (!ptr.toWord().and(Word.fromIntZeroExtend(BYTES_IN_ADDRESS - 1)).isZero()) {
      byte bits = ptr.loadByte(Offset.fromIntZeroExtend(length));
      if (bits == 0) {
        return length;
      }
      length++;
    }
    // Ascii characters are normally in the range 1 to 128, if we subtract 1
    // from each byte and look if the top bit of the byte is set then if it is
    // the chances are the byte's value is 0. Loop over words doing this quick
    // test and then do byte by byte tests when we think we have the 0
    Word onesToSubtract;
    Word maskToTestHighBits;
    if (VM.BuildFor32Addr) {
      onesToSubtract     = Word.fromIntZeroExtend(0x01010101);
      maskToTestHighBits = Word.fromIntZeroExtend(0x80808080);
    } else {
      onesToSubtract     = Word.fromLong(0x0101010101010101L);
      maskToTestHighBits = Word.fromLong(0x8080808080808080L);
    }
    while (true) {
      Word bytes = ptr.loadWord(Offset.fromIntZeroExtend(length));
      if(!bytes.minus(onesToSubtract).and(maskToTestHighBits).isZero()) {
        if (VM.LittleEndian) {
          for(int byteOff=0; byteOff < BYTES_IN_ADDRESS; byteOff++) {
            if(bytes.and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + byteOff;
            }
            bytes = bytes.rshl(8);
          }
        } else {
          for(int byteOff=BYTES_IN_ADDRESS-1; byteOff >= 0; byteOff--) {
            if(bytes.rshl(byteOff*8).and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + (BYTES_IN_ADDRESS - 1 - byteOff);
            }
          }
        }
      }       
      length += BYTES_IN_ADDRESS;
    } 
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  public static byte[] createByteArrayFromC(Address stringAddress) {

    int length = strlen(stringAddress);
    byte[] contents = new byte[length];
    VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), stringAddress, length);

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
    if (val) {
      boolPtr.store((byte)1);
    } else {
      boolPtr.store((byte)0);
    }
  }
}
