/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.utility;

import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;

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
  private static boolean isSetHelper(Address start, int size, boolean verbose, 
				     Word v) throws NoInlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert((size & (BYTES_IN_WORD-1)) == 0);
    for (int i=0; i < size; i += BYTES_IN_WORD) 
      if (start.loadWord(Offset.fromInt(i)).NE(v)) {
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
    return isSetHelper(start, size, false, Word.zero());
  }

  // this is in the inline allocation sequence when VM_Interface.VerifyAssertions
  // therefore it is very carefully written to reduce the impact on code space.
  public static void assertIsZeroed(Address start, int size) throws NoInlinePragma {
    Assert._assert(isSetHelper(start, size, true, Word.zero()));
  }

  public static boolean assertIsSet(Address start, int size, int v) throws InlinePragma {
    return isSetHelper(start, size, true, Word.fromInt(v));
  }

  public static void zeroSmall(Address start, Extent len) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS)
      Assert._assert((len.toInt() & (BYTES_IN_WORD-1)) == 0
                     && (start.toInt() & (BYTES_IN_WORD-1)) == 0);
    Address end = start.add(len);
    for (Address i = start; i.LT(end); i = i.add(BYTES_IN_WORD)) 
      i.store(Word.zero());
  }

  public static void set (Address start, int len, int v) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS)
      Assert._assert((len & (BYTES_IN_WORD-1)) == 0
                     && (start.toInt() & (BYTES_IN_WORD-1)) == 0);
    for (int i=0; i < len; i += BYTES_IN_WORD) 
      start.store(Word.fromInt(v), Offset.fromInt(i));
  }

  // start and len must both be 4-byte aligned
  //
  public static void zero(Address start, Extent len) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS)
      Assert._assert((len.toInt() & (BYTES_IN_WORD-1)) == 0
                     && (start.toInt() & (BYTES_IN_WORD-1)) == 0);
    if (len.GT(Extent.fromIntZeroExtend(256))) 
      org.mmtk.vm.Memory.zero(start, len);
    else
      zeroSmall(start, len);
  }

  // start and len must both be OS-page aligned
  //
  public static void zeroPages(Address start, int len) throws InlinePragma {
    org.mmtk.vm.Memory.zeroPages(start, len);
  }

  public static void dumpMemory(Address addr, int before, int after) {
    org.mmtk.vm.Memory.dumpMemory(addr, before, after);
  }

}
