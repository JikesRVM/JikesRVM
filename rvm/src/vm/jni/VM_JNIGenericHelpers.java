/*
 * (C) Copyright IBM Corp 2001,2002,2003
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;

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
  static byte[] createByteArrayFromC(VM_Address stringAddress) {
    // scan the memory for the null termination of the string
    int length = 0;
    for (VM_Address addr = stringAddress; true; addr = addr.add(4)) {
      int word = VM_Magic.getMemoryInt(addr);
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
  static String createStringFromC(VM_Address stringAddress) {
    return new String(createByteArrayFromC(stringAddress));
  }
}
