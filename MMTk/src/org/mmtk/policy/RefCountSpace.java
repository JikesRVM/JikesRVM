/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * Each instance of this class corresponds to one reference counted
 * *space*.  In other words, it maintains and performs actions with
 * respect to state that is global to a given reference counted space.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the RefCountLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of RefCountLocal.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class RefCountSpace implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  public boolean bootImageMark = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  RefCountSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //
  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final int getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
    return 0;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a new collection increment.  Flip the state of the
   * boot image mark bit (use for debug tracing).
   */
  public void prepare() { 
    if (!Options.noFinalizer)
      VM_Interface.sysFail("-X:gc:noFinalizer must be used with RefCount Plan");
    bootImageMark = !bootImageMark;
  }

  /**
   * A new collection increment has completed.
   */
  public void release() {
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * An object has been encountered in a traversal of the object
   * graph.  If this reference is from a root, perform an increment
   * and add the object to the root set.
   *
   * @param object The object encountered in the trace
   * @param root True if the object is referenced directly from a root
   */
  public final VM_Address traceObject(VM_Address object, boolean root)
    throws VM_PragmaInline {
    if (Plan.sanityTracing) 
      incrementTraceCount(object);

    increment(object);
    if (root)
      VM_Interface.getPlan().addToRootSet(object);

    return object;
  }

  /**
   * Determine whether an object is live.
   *
   * @param object The object in question
   * @return True if this object is considered live (i.e. it has a no-zero RC)
   */
  public static boolean isLive(VM_Address object)
    throws VM_PragmaInline {
    return RCBaseHeader.isLiveRC(object);
  }
  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Methods for sanity tracing (tracing to check ref counts)
  //

  /**
   * An (reference counted) object has been encountered in a sanity
   * trace, increment its reachability count and enqueue for recursive
   * scanning if this is the first tracing of the object
   *
   * @param object The object to be traced
   */
  public final void incrementTraceCount(VM_Address object) 
    throws VM_PragmaInline {
    if (RCBaseHeader.incTraceRC(object)) {
      VM_Interface.getPlan().addToTraceBuffer(object); 
      Plan.enqueue(object);
    }
  }

  /**
   * A boot image (or immortal) object has been encountered in a
   * sanity trace.  Set the mark bit if necessary (at present this is
   * a hack---we use the buffered bit).  FIXME
   *
   * For consistency with existing interfaces, return the object.
   *
   * @param object The boot or immortal object encountered.
   * @return The object (a no-op in this case).
   */
  public final VM_Address traceBootObject(VM_Address object) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(Plan.sanityTracing);
    if (bootImageMark && !RCBaseHeader.isBuffered(object)) {
      RCBaseHeader.setBufferedBit(object);
      Plan.enqueue(object);
    } else if (!bootImageMark && RCBaseHeader.isBuffered(object)) {
      RCBaseHeader.clearBufferedBit(object);
      Plan.enqueue(object);
    }
    return object;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Misc
  //

  /**
   * Increment the reference count for an object.
   *
   * @param object  The object whose reference count is to be incremented
   */
  public final void increment(VM_Address object) 
    throws VM_PragmaInline {
    RCBaseHeader.incRC(object);
    if (Plan.refCountCycleDetection && !RCBaseHeader.isGreen(object))
      RCBaseHeader.makeBlack(object);
  }

  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}
}
