/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;


import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Magic;

/**
 * This class implements tracing for a simple immortal collection
 * policy.  Under this policy all that is required is for the
 * "collector" to propogate marks in a liveness trace.  It does not
 * actually collect.  This class does not hold any state, all methods
 * are static.
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class ImmortalSpace extends BasePolicy 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 


  /****************************************************************************
   *
   * Object header manipulations
   */

  /**
   * test to see if the mark bit has the given value
   */
  private static boolean testMarkBit(VM_Address ref, VM_Word value) {
    return !(VM_Interface.readAvailableBitsWord(ref).and(value).isZero());
  }

  /**
   * write the given value in the mark bit.
   */
  private static void writeMarkBit(VM_Address ref, VM_Word value) {
    VM_Word oldValue = VM_Interface.readAvailableBitsWord(ref);
    VM_Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
    VM_Interface.writeAvailableBitsWord(ref,newValue);
  }

  /**
   * atomically write the given value in the mark bit.
   */
  private static void atomicWriteMarkBit(VM_Address ref, VM_Word value) {
    while (true) {
      VM_Word oldValue = VM_Interface.prepareAvailableBits(ref);
      VM_Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
      if (VM_Interface.attemptAvailableBits(ref,oldValue,newValue)) break;
    }
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects during GC
   * Returns true if marking was done.
   */
  private static boolean testAndMark(VM_Address ref, VM_Word value) 
    throws VM_PragmaInline {
    VM_Word oldValue;
    do {
      oldValue = VM_Interface.prepareAvailableBits(ref);
      VM_Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM_Interface.attemptAvailableBits(ref,oldValue,oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

  static final VM_Word GC_MARK_BIT_MASK    = VM_Word.one();
  private static VM_Word immortalMarkState = VM_Word.zero(); // when GC off, the initialization value


  /**
   * Trace a reference to an object under an immortal collection
   * policy.  If the object is not already marked, enqueue the object
   * for subsequent processing. The object is marked as (an atomic)
   * side-effect of checking whether already marked.
   *
   * @param object The object to be traced.
   */

  public static VM_Address traceObject(VM_Address object) {
    if (testAndMark(object, immortalMarkState)) 
      VM_Interface.getPlan().enqueue(object);
    return object;
  }

  public static void postAlloc (VM_Address object) throws VM_PragmaInline {
    writeMarkBit (object, immortalMarkState);
  }

  /**
   * Prepare for a new collection increment.  For the immortal
   * collector we must flip the state of the mark bit between
   * collections.
   */
  public static void prepare(VMResource vm, MemoryResource mr) { 
    immortalMarkState = GC_MARK_BIT_MASK.sub(immortalMarkState);
  }

  public static void release(VMResource vm, MemoryResource mr) { 
  }

  public static boolean isLive(VM_Address obj) {
    return true;
  }

}
