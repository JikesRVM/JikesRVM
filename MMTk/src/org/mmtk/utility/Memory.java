/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;


/*
 * @author Perry Cheng  
 */  

public class Memory implements VM_Uninterruptible {

  private static boolean isZeroedHelper(VM_Address start, EXTENT size, boolean verbose) {
    if (VM.VerifyAssertions) VM._assert(size == (size & (~3)));
    for (int i=0; i<size; i+=4) 
      if (!VM_Magic.getMemoryAddress(start.add(i)).isZero()) {
	if (verbose) {
	  VM.sysWrite("Memory range is not zeroed: ", start);
	  VM.sysWriteln(" .. ", start.add(size));
	  dumpMemory(start, 0, size);
	}
	return false;
      }
    return true;
  }

  static boolean IsZeroed(VM_Address start, EXTENT size) {
    return isZeroedHelper(start, size, false);
  }

  static boolean assertIsZeroed(VM_Address start, EXTENT size) {
    return isZeroedHelper(start, size, true);
  }

  static void zero(VM_Address start, VM_Address end) {
    VM_Memory.zero(start, end);
  }

  static void zero(VM_Address start, int len) {
    VM_Memory.zero(start, len);
  }

  public static void dumpMemory(VM_Address addr, int before, int after) {
    VM_Memory.dumpMemory(addr, before, after);
  }

}
