/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package org.mmtk.policy;

import org.mmtk.utility.heap.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing for a simple immortal collection
 * policy.  Under this policy all that is required is for the
 * "collector" to propogate marks in a liveness trace.  It does not
 * actually collect.  This class does not hold any state, all methods
 * are static.
 *
 * $Id$ 
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class ImmortalSpace extends BasePolicy 
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Object header manipulations
   */

  /**
   * test to see if the mark bit has the given value
   */
  private static boolean testMarkBit(Address ref, Word value) {
    return !(ObjectModel.readAvailableBitsWord(ref).and(value).isZero());
  }

  /**
   * write the given value in the mark bit.
   */
  private static void writeMarkBit(Address ref, Word value) {
    Word oldValue = ObjectModel.readAvailableBitsWord(ref);
    Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
    ObjectModel.writeAvailableBitsWord(ref,newValue);
  }

  /**
   * atomically write the given value in the mark bit.
   */
  private static void atomicWriteMarkBit(Address ref, Word value) {
    while (true) {
      Word oldValue = ObjectModel.prepareAvailableBits(ref);
      Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
      if (ObjectModel.attemptAvailableBits(ref,oldValue,newValue)) break;
    }
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects during GC
   * Returns true if marking was done.
   */
  private static boolean testAndMark(Address ref, Word value) 
    throws InlinePragma {
    Word oldValue;
    do {
      oldValue = ObjectModel.prepareAvailableBits(ref);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!ObjectModel.attemptAvailableBits(ref,oldValue,oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

  static final Word GC_MARK_BIT_MASK    = Word.one();
  public static Word immortalMarkState = Word.zero(); // when GC off, the initialization value


  /**
   * Trace a reference to an object under an immortal collection
   * policy.  If the object is not already marked, enqueue the object
   * for subsequent processing. The object is marked as (an atomic)
   * side-effect of checking whether already marked.
   *
   * @param object The object to be traced.
   */

  public static Address traceObject(Address object) {
    if (testAndMark(object, immortalMarkState)) 
      Plan.enqueue(object);
    return object;
  }

  public static void postAlloc (Address object) throws InlinePragma {
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

  public static boolean isLive(Address obj) {
    return true;
  }

  /**
   * Returns if the object in question is currently thought to be reachable.  
   * This is done by comparing the mark bit to the current mark state. For the 
   * immortal collector reachable and live are different, making this method
   * necessary.
   *
   * @param ref The address of an object in immortal space to test
   * @return True if <code>ref</code> may be a reachable object (e.g., having
   *         the current mark state).  While all immortal objects are live,
   *         some may be unreachable.
   */
  public static boolean isReachable(Address ref) {
    return (ObjectModel.readAvailableBitsWord(ref).and(GC_MARK_BIT_MASK).EQ(immortalMarkState));
  }
}
