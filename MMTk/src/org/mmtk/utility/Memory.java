/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.utility;

import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 * @author Perry Cheng  
 */  

public class Memory implements Uninterruptible, Constants {

  /* Inlining this loop into the uninterruptible code can cause/encourage the
   GCP into moving a get_obj_tib into the interruptible region where the tib
   is being installed via an int_store 
  */
  private static boolean isSetHelper(Address start, int size, boolean verbose, int v) throws NoInlinePragma {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert((size & (BYTES_IN_INT-1)) == 0);
    for (int i=0; i < size; i += BYTES_IN_INT) 
      if (start.loadInt(Offset.fromInt(i)) != v) {
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

  public static boolean IsZeroed(Address start, int size) throws InlinePragma {
    return isSetHelper(start, size, false, 0);
  }

  // this is in the inline allocation sequence when VM_Interface.VerifyAssertions
  // therefore it is very carefully written to reduce the impact on code space.
  public static void assertIsZeroed(Address start, int size) throws NoInlinePragma {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(isSetHelper(start, size, true, 0));
  }

  public static boolean assertIsSet(Address start, int size, int v) throws InlinePragma {
    return isSetHelper(start, size, true, v);
  }

  public static void zeroSmall(Address start, Extent len) throws InlinePragma {
    Address end = start.add(len);
    for (Address i = start; i.LT(end); i = i.add(BYTES_IN_INT)) 
      i.store(0);
  }

  public static void set (Address start, int len, int v) throws InlinePragma {
    for (int i=0; i < len; i += BYTES_IN_INT) 
      start.store(v, Offset.fromInt(i));
  }

  // start and len must both be 4-byte aligned
  //
  public static void zero(Address start, Extent len) throws InlinePragma {
    if (len.GT(Extent.fromIntZeroExtend(256))) 
      VM_Interface.zero(start, len);
    else
      zeroSmall(start, len);
  }

  // start and len must both be OS-page aligned
  //
  public static void zeroPages(Address start, int len) throws InlinePragma {
    VM_Interface.zeroPages(start, len);
  }

  public static void dumpMemory(Address addr, int before, int after) {
    VM_Interface.dumpMemory(addr, before, after);
  }

}
