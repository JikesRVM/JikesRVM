/**
  * (C) Copyright Department of Computer Science,
  * Australian National University. 2004.
  */
package org.mmtk.utility;

import org.mmtk.vm.TraceInterface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class specializes SortSharedQueue to sort objects according to
 * their time of death (TOD).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
final public class SortTODSharedDeque extends SortSharedDeque 
  implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  private static final int BYTES_PUSHED = BYTES_IN_ADDRESS * 5;
  private static final int MAX_STACK_SIZE = BYTES_PUSHED * 64;
  private static final VM_Offset INSERTION_SORT_LIMIT = VM_Offset.fromInt(80);
  
  /**
   * Constructor
   *
   * @param rpa The allocator from which the instance should obtain buffers.
   * @param airty The arity of the data to be enqueued
   */
  public SortTODSharedDeque(RawPageAllocator rpa, int arity) {
    super(rpa, arity);
  }
  
  /**
   * Return the sorting key for the object passed as a parameter.
   *
   * @param obj The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  protected final VM_Word getKey(VM_Address obj) {
    return TraceInterface.getDeathTime(obj);
  }
}
