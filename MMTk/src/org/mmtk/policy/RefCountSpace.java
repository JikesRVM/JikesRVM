/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
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
      VM.sysFail("-X:gc:noFinalizer must be used with RefCount Plan");
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
  public final VM_Address traceObject(VM_Address object, boolean root)
    throws VM_PragmaInline {

    if (Plan.sanityTracing) 
      incrementTraceCount(object);

    if (root) {
      increment(object);
      VM_Interface.getPlan().addToRootSet(object);
    } // else we were called via the finalizer mechanism, we need to ignore

    return object;
  }

  public static boolean isLive(VM_Address obj)
    throws VM_PragmaInline {
    return RCBaseHeader.isLiveRC(obj);
  }
  
  public final void incrementTraceCount(VM_Address object) 
    throws VM_PragmaInline {
    if (RCBaseHeader.incTraceRC(object)) {
      VM_Interface.getPlan().addToTraceBuffer(object); 
      Plan.enqueue(object);
    }
  }
  public final VM_Address traceBootObject(VM_Address object) {
    if (VM.VerifyAssertions) VM._assert(Plan.sanityTracing);
    if (bootImageMark && !RCBaseHeader.isBuffered(object)) {
      RCBaseHeader.setBufferedBit(object);
      Plan.enqueue(object);
    } else if (!bootImageMark && RCBaseHeader.isBuffered(object)) {
      RCBaseHeader.clearBufferedBit(object);
      Plan.enqueue(object);
    }
    return object;
  }
  public final void increment(VM_Address object) 
    throws VM_PragmaInline {
    RCBaseHeader.incRC(object);
    if (Plan.refCountCycleDetection && !RCBaseHeader.isGreen(object))
      RCBaseHeader.makeBlack(object);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Misc
  //
  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}
}
