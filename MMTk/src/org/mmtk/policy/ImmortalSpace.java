/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002, 2003, 2004
 */
package org.mmtk.policy;

import org.mmtk.utility.heap.MonotonePageResource;
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
public final class ImmortalSpace extends Space 
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  static final Word GC_MARK_BIT_MASK = Word.one();
  public static Word immortalMarkState = Word.zero(); // when GC off, the initialization value

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  public ImmortalSpace(String name, int pageBudget, Address start,
		       Extent bytes) {
    super(name, false, true, start, bytes);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }
  
  /**
   * Construct a space of a given number of megabytes in size.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  public ImmortalSpace(String name, int pageBudget, int mb) {
    super(name, false, true, mb);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  public ImmortalSpace(String name, int pageBudget, float frac) {
    super(name, false, true, frac);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public ImmortalSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, true, mb, top);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public ImmortalSpace(String name, int pageBudget, float frac, boolean top) {
    super(name, false, true, frac, top);
    pr = new MonotonePageResource(pageBudget, this, start, extent);
  }

  /****************************************************************************
   *
   * Object header manipulations
   */

  /**
   * test to see if the mark bit has the given value
   */
  private static boolean testMarkBit(ObjectReference object, Word value) {
    return !(ObjectModel.readAvailableBitsWord(object).and(value).isZero());
  }

  /**
   * write the given value in the mark bit.
   */
  private static void writeMarkBit(ObjectReference object, Word value) {
    Word oldValue = ObjectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
    ObjectModel.writeAvailableBitsWord(object, newValue);
  }

  /**
   * atomically write the given value in the mark bit.
   */
  private static void atomicWriteMarkBit(ObjectReference object, Word value) {
    while (true) {
      Word oldValue = ObjectModel.prepareAvailableBits(object);
      Word newValue = oldValue.and(GC_MARK_BIT_MASK.not()).or(value);
      if (ObjectModel.attemptAvailableBits(object, oldValue, newValue)) break;
    }
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects during GC
   * Returns true if marking was done.
   */
  private static boolean testAndMark(ObjectReference object, Word value) 
    throws InlinePragma {
    Word oldValue;
    do {
      oldValue = ObjectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!ObjectModel.attemptAvailableBits(object, oldValue,
                                               oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }



  /**
   * Trace a reference to an object under an immortal collection
   * policy.  If the object is not already marked, enqueue the object
   * for subsequent processing. The object is marked as (an atomic)
   * side-effect of checking whether already marked.
   *
   * @param object The object to be traced.
   */
  public final ObjectReference traceObject(ObjectReference object) 
    throws InlinePragma {
    if (testAndMark(object, immortalMarkState)) 
      Plan.enqueue(object);
    return object;
  }

  public static void postAlloc(ObjectReference object) throws InlinePragma {
    writeMarkBit (object, immortalMarkState);
  }

  /**
   * Prepare for a new collection increment.  For the immortal
   * collector we must flip the state of the mark bit between
   * collections.
   */
  public void prepare() { 
    immortalMarkState = GC_MARK_BIT_MASK.sub(immortalMarkState);
  }

  public void release() { 
  }

  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   *
   * @param start The address of the start of the page or pages
   */
  public final void release(Address start) throws InlinePragma {
    Assert._assert(false);  // this policy only releases pages enmasse
  }

  public final boolean isLive(ObjectReference object) throws InlinePragma {
    return true;
  }

  /**
   * Returns if the object in question is currently thought to be reachable.  
   * This is done by comparing the mark bit to the current mark state. For the 
   * immortal collector reachable and live are different, making this method
   * necessary.
   *
   * @param object The address of an object in immortal space to test
   * @return True if <code>ref</code> may be a reachable object (e.g., having
   *         the current mark state).  While all immortal objects are live,
   *         some may be unreachable.
   */
  public static boolean isReachable(ObjectReference object) {
    return (ObjectModel.readAvailableBitsWord(object).and(GC_MARK_BIT_MASK).EQ(immortalMarkState));
  }
}
