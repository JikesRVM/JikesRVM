/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

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
final class TreadmillSpace implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private VM_Word markState;
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
  TreadmillSpace(FreeListVMResource vmr, MemoryResource mr) {
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
  public final void postAlloc(VM_Address cell, TreadmillLocal thread)
    throws VM_PragmaInline {
    thread.treadmill.addToFromSpace(Treadmill.payloadToNode(cell));
  }

  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final VM_Word getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
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
   * @param vm (unused)
   * @param mr (unused)
   */
  public void prepare(VMResource vm, MemoryResource mr) { 
    markState = MarkSweepHeader.MARK_BIT_MASK.sub(markState);
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
    throws VM_PragmaInline {
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
  public final VM_Address traceObject(VM_Address object)
    throws VM_PragmaInline {
    if (MarkSweepHeader.testAndMark(object, markState)) {
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
   public boolean isLive(VM_Address obj)
    throws VM_PragmaInline {
     return MarkSweepHeader.testMarkBit(obj, markState);
   }

  /**
   * An object has been marked (identified as live).  Large objects
   * are added to the to-space treadmill, while all other objects will
   * have a mark bit set in the superpage header.
   *
   * @param object The object which has been marked.
   */
  private final void internalMarkObject(VM_Address object) 
    throws VM_PragmaInline {

    // VM_Address ref = VM_Interface.refToAddress(object);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!MarkSweepHeader.isSmallObject(object));
        
    VM_Address cell = VM_Interface.objectStartRef(object);
    VM_Address node = Treadmill.midPayloadToNode(cell);
    Treadmill tm = Treadmill.getTreadmill(node);
    tm.copy(node);
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
    throws VM_PragmaInline {
    return vmResource;
  }

  /**
   * Return the memory resource associated with this collector
   *
   * @return The memory resource associated with this collector
   */
  public final MemoryResource getMemoryResource() 
    throws VM_PragmaInline {
    return memoryResource;
  }
}
