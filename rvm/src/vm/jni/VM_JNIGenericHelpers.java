/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;

import org.vmmagic.unboxed.*;

/**
 * Platform independent utility functions called from VM_JNIFunctions
 * (cannot be placed in VM_JNIFunctions because methods 
 * there are specially compiled to be called from native).
 * 
 * @see VM_JNIFunctions
 * 
 * @author Dave Grove
 * @author Ton Ngo
 * @author Steve Smith 
 */
abstract class VM_JNIGenericHelpers {

  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string.
   * 
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  static byte[] createByteArrayFromC(Address stringAddress) {
    // scan the memory for the null termination of the string
    int length = 0;
    for (Address addr = stringAddress; true; addr = addr.add(4)) {
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
      if (byte0==0)
        break;
      length++;
      if (byte1==0) 
        break;
      length++;
      if (byte2==0)
        break;
      length++;
      if (byte3==0)
        break;
      length++;
    }

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
  static String createStringFromC(Address stringAddress) {
    return new String(createByteArrayFromC(stringAddress));
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
    if (boolPtr.isZero())
      return;
    int temp = boolPtr.loadInt();
    int intval;
    if (VM.LittleEndian) {
      if (val)                  // set to true.
        intval = (temp & 0xffffff00) | 0x00000001;
      else                      // set to false
        intval = (temp & 0xffffff00);
    } else {
      /* Big Endian */
      if (val)                  // set to true
        intval = (temp & 0x00ffffff) | 0x01000000;
      else                      // set to false
        intval =  temp & 0x00ffffff;
    }
    boolPtr.store(intval);
  }
}
