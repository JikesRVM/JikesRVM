/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.heap.*;
import org.mmtk.utility.Treadmill;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class TreadmillSpace implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

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
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private boolean inTreadmillCollection = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  public TreadmillSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   * @param thread The treadmill thread instance through which
   * this instance was allocated.
   */
  public final void postAlloc(Address cell, TreadmillLocal thread)
    throws InlinePragma {
    thread.treadmill.addToFromSpace(Treadmill.payloadToNode(cell));
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
   * @param vm (unused)
   * @param mr (unused)
   */
  public void prepare(VMResource vm, MemoryResource mr) { 
    markState = MARK_BIT_MASK.sub(markState);
    inTreadmillCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param vm (unused)
   * @param mr (unused)
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
      VM_Interface.getPlan().enqueue(object);
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
    Address cell = VM_Interface.objectStartRef(object);
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
    Word oldValue = VM_Interface.readAvailableBitsWord(object);
    Word newValue = oldValue.and(MARK_BIT_MASK.not()).or(markState);
    VM_Interface.writeAvailableBitsWord(object, newValue);
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
      oldValue = VM_Interface.prepareAvailableBits(object);
      markBit = oldValue.and(MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM_Interface.attemptAvailableBits(object, oldValue,
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
    return VM_Interface.readAvailableBitsWord(object).and(MARK_BIT_MASK).EQ(value);
  }

 /****************************************************************************
   *
   * Miscellaneous
   */
  
  /**
   * Return the VMResource associated with this collector
   *
   * @return the VMResource associated with this collector
   */
  public final FreeListVMResource getVMResource() 
    throws InlinePragma {
    return vmResource;
  }

  /**
   * Return the memory resource associated with this collector
   *
   * @return The memory resource associated with this collector
   */
  public final MemoryResource getMemoryResource() 
    throws InlinePragma {
    return memoryResource;
  }
}
