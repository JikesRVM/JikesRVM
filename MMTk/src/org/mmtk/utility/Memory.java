/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Magic;


/*
 * @author Perry Cheng  
 */  

public class Memory implements VM_Uninterruptible {

  /* Inlining this loop into the uninterruptible code can cause/encourage the
   GCP into moving a get_obj_tib into the interruptible region where the tib
   is being installed via an int_store 
  */
  private static boolean isSetHelper(VM_Address start, int size, boolean verbose, int v) throws VM_PragmaNoInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(size == (size & (~3)));
    for (int i=0; i<size; i+=4) 
      if (VM_Magic.getMemoryInt(start.add(i)) != v) {
        if (verbose) {
            Log.prependThreadId();
            Log.write("Memory range does not contain only value ");
            Log.writeln(v);
            Log.write("Non-zero range: "); Log.write(start);
            Log.write(" .. "); Log.writeln(start.add(size));
            Log.write("First bad value at "); Log.writeln(start.add(i));
            dumpMemory(start, 0, size);
        }
        return false;
      }
    return true;
  }

  public static boolean IsZeroed(VM_Address start, int size) throws VM_PragmaInline {
    return isSetHelper(start, size, false, 0);
  }

  // this is in the inline allocation sequence when VM_Interface.VerifyAssertions
  // therefore it is very carefully written to reduce the impact on code space.
  public static void assertIsZeroed(VM_Address start, int size) throws VM_PragmaNoInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(isSetHelper(start, size, true, 0));
  }

  public static boolean assertIsSet(VM_Address start, int size, int v) throws VM_PragmaInline {
    return isSetHelper(start, size, true, v);
  }

  public static void zeroSmall(VM_Address start, VM_Extent len) throws VM_PragmaInline {
    for (int i=0; VM_Extent.fromIntZeroExtend(i).LT(len); i+=4) 
      VM_Magic.setMemoryInt(start.add(i), 0);
  }

  public static void set (VM_Address start, int len, int v) throws VM_PragmaInline {
    for (int i=0; i<len; i+=4) 
      VM_Magic.setMemoryInt(start.add(i), v);
  }

  // start and len must both be 4-byte aligned
  //
  public static void zero(VM_Address start, VM_Extent len) throws VM_PragmaInline {
    if (len.GT(VM_Extent.fromIntZeroExtend(256))) 
      VM_Interface.zero(start, len);
    else
      zeroSmall(start, len);
  }

  // start and len must both be OS-page aligned
  //
  public static void zeroPages(VM_Address start, int len) throws VM_PragmaInline {
    VM_Interface.zeroPages(start, len);
  }

  public static void dumpMemory(VM_Address addr, int before, int after) {
    VM_Interface.dumpMemory(addr, before, after);
  }

}
