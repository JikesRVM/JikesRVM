/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.plan.MarkSweepHeader;
import org.mmtk.plan.Plan;
import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Memory;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class MarkSweepSpace implements Constants, VM_Uninterruptible {
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
  public boolean inMSCollection = false;

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
  public MarkSweepSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit.
   */
  public final VM_Word getInitialHeaderValue() 
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
  public void prepare(FreeListVMResource vm, MemoryResource mr) { 
    markState = MarkSweepHeader.MARK_BIT_MASK.sub(markState);
    MarkSweepLocal.zeroLiveBits(vm);
    inMSCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param vm (unused)
   * @param mr (unused)
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
    throws VM_PragmaInline {
    return inMSCollection;
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
    if (MarkSweepLocal.testAndMarkLiveObject(object)) {
      if (Plan.GATHER_MARK_CONS_STATS)
	Plan.mark.inc(VM_Interface.getSizeWhenCopied(object));
      VM_Interface.getPlan().enqueue(object);
    }
    return object;
  }
  
  /**
   *
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
   public boolean isLive(VM_Address object)
    throws VM_PragmaInline {
     return MarkSweepLocal.isLiveObject(object);
   }

  /****************************************************************************
   *
   * Misc
   */
  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}

}
