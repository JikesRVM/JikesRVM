/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.plan.RCBaseHeader;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.heap.MemoryResource;
import org.mmtk.utility.heap.FreeListVMResource;
import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
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
public final class RefCountSpace implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean INC_DEC_ROOT = false;
  public static final boolean RC_SANITY_CHECK = false;
  
  /****************************************************************************
   *
   * Instance variables
   */
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  public boolean bootImageMark = false;

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
  public RefCountSpace(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  /****************************************************************************
   *
   * Allocation
   */
  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final VM_Word getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
    return VM_Word.zero();
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a new collection increment.  Nothing to do.
   */
  public void prepare() {}

  /**
   * A new collection increment has completed.
   */
  public void release() {
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * An object has been encountered in a traversal of the object
   * graph.  If this reference is from a root, perform an increment
   * and add the object to the root set.
   *
   * @param object The object encountered in the trace
   * @param root True if the object is referenced directly from a root
   */
  public final VM_Address traceObject(VM_Address object)
    throws VM_PragmaInline {
    if (INC_DEC_ROOT) {
      RCBaseHeader.incRC(object);
      VM_Interface.getPlan().addToRootSet(object);
    } else if (RCBaseHeader.setRoot(object)) {
      VM_Interface.getPlan().addToRootSet(object);
    }
    return object;
  }

  /****************************************************************************
   *
   * Misc
   */

  public final FreeListVMResource getVMResource() { return vmResource;}
  public final MemoryResource getMemoryResource() { return memoryResource;}
}
