/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.Memory;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 *
 *  $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class MarkSweepSpace extends Space
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 1;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_BYTES_REQUIRED = 0;
  public static final Word MARK_BIT_MASK = Word.one();  // ...01

  /****************************************************************************
   *
   * Instance variables
   */
  private Word markState;
  public boolean inMSCollection = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   */
  public MarkSweepSpace(String name, int pageBudget, Address start,
			Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  public MarkSweepSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }
   
  public MarkSweepSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }
  
  public MarkSweepSpace(String name, int pageBudget, float frac) {
    super(name, false, false, frac);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }
   
  public MarkSweepSpace(String name, int pageBudget, float frac, boolean top) {
    super(name, false, false, frac, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /****************************************************************************
   *
   * Allocation
   */

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
    
    MarkSweepLocal.zeroLiveBits(start, ((FreeListPageResource) pr).getHighWater());
    inMSCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   */
  public void release() {
    inMSCollection = false;
  }

  /**
   * Return true if this mark-sweep space is currently being collected.
   *
   * @return True if this mark-sweep space is currently being collected.
   */
  public boolean inMSCollection() 
    throws InlinePragma {
    return inMSCollection;
  }

  public void release(Address start) {
    ((FreeListPageResource) pr).releasePages(start); 
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
      if (Stats.GATHER_MARK_CONS_STATS)
	Plan.mark.inc(ObjectModel.getSizeWhenCopied(object));
      MarkSweepLocal.liveObject(object);
      Plan.enqueue(object);
    }
    return object;
  }

  /**
   *
   * @param obj The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  public boolean isLive(Address obj)
    throws InlinePragma {
    return testMarkBit(obj, markState);
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
  private static boolean testAndMark(Address object, Word value)
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
  private static boolean testMarkBit(Address object, Word value)
    throws InlinePragma {
    return ObjectModel.readAvailableBitsWord(object).and(MARK_BIT_MASK).EQ(value);
  }

  /**
   * Write a given value in the mark bit of an object non-atomically
   *
   * @param object The object whose mark bit is to be written
   */
  public void writeMarkBit(Address object) throws InlinePragma {
    Word oldValue = ObjectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(MARK_BIT_MASK.not()).or(markState);
    ObjectModel.writeAvailableBitsWord(object, newValue);
  }

}
