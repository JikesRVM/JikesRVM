/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.Treadmill;
import org.mmtk.vm.Constants;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one treadmill *space*.
 *
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).
 *
 * This stands in contrast to TreadmillLocal, which is instantiated
 * and called on a per-thread basis, where each instance of
 * TreadmillLocal corresponds to one thread operating over one space.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class LargeObjectSpace extends Space 
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 1;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final Word MARK_BIT_MASK = Word.one();  // ...01

  /****************************************************************************
   *
   * Instance variables
   */
  private Word markState;
  private boolean inTreadmillCollection = false;

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
  public LargeObjectSpace(String name, int pageBudget, Address start,
			  Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
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
  public LargeObjectSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
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
  public LargeObjectSpace(String name, int pageBudget, float frac) {
    super(name, false, false, frac);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
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
  public LargeObjectSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
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
  public LargeObjectSpace(String name, int pageBudget, float frac, 
			  boolean top) {
    super(name, false, false, frac, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent);
  }

  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final Word getInitialHeaderValue(int size) 
    throws InlinePragma {
      return markState;
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   *
   */
  public void prepare() { 
    markState = MARK_BIT_MASK.sub(markState);
    inTreadmillCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   */
  public void release() {
    inTreadmillCollection = false;
  }

  /**
   * Return true if this mark-sweep space is currently being collected.
   *
   * @return True if this mark-sweep space is currently being collected.
   */
  public boolean inTreadmillCollection() 
    throws InlinePragma {
    return inTreadmillCollection;
  }

  /**
   * Release a group of pages that were allocated together.
   *
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  public final void release(Address first) throws InlinePragma {
    ((FreeListPageResource) pr).releasePages(first);
  }


  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference to an object under a mark sweep collection
   * policy.  If the object header is not already marked, mark the
   * object in either the bitmap or by moving it off the treadmill,
   * and enqueue the object for subsequent processing. The object is
   * marked as (an atomic) side-effect of checking whether already
   * marked.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  public final Address traceObject(Address object)
    throws InlinePragma {
    if (testAndMark(object, markState)) {
      internalMarkObject(object);
      Plan.getInstance().enqueue(object);
    }
    return object;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param obj The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
   public boolean isLive(Address obj)
    throws InlinePragma {
     return testMarkBit(obj, markState);
   }

  /**
   * An object has been marked (identified as live).  Large objects
   * are added to the to-space treadmill, while all other objects will
   * have a mark bit set in the superpage header.
   *
   * @param object The object which has been marked.
   */
  private final void internalMarkObject(Address object) 
    throws InlinePragma {

    Address cell = ObjectModel.objectStartRef(object);
    Address node = Treadmill.midPayloadToNode(cell);
    Treadmill tm = Treadmill.getTreadmill(node);
    tm.copy(node);
  }

  /****************************************************************************
   *
   * Header manipulation
   */

   /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param object the object ref to the storage to be initialized
   */
  public final void initializeHeader(Address object) 
    throws InlinePragma {
    Word oldValue = ObjectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(MARK_BIT_MASK.not()).or(markState);
    ObjectModel.writeAvailableBitsWord(object, newValue);
  }

  /**
   * Atomically attempt to set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param object The object whose mark bit is to be written
   * @param value The value to which the mark bit will be set
   */
  public static boolean testAndMark(Address object, Word value)
    throws InlinePragma {
    Word oldValue, markBit;
    do {
      oldValue = ObjectModel.prepareAvailableBits(object);
      markBit = oldValue.and(MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!ObjectModel.attemptAvailableBits(object, oldValue,
                                               oldValue.xor(MARK_BIT_MASK)));
    return true;
  }

  /**
   * Return true if the mark bit for an object has the given value.
   *
   * @param object The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return True if the mark bit for the object has the given value.
   */
  static public boolean testMarkBit(Address object, Word value)
    throws InlinePragma {
    return ObjectModel.readAvailableBitsWord(object).and(MARK_BIT_MASK).EQ(value);
  }
}
