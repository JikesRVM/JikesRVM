/*
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This supports <i>unsynchronized</i> pushing and popping of addresses. 
 * In addition, this can sort the entries currently on the shared stack.
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class SortTODAddressStack extends LocalDeque 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 
 
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared stack to which this stack will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  SortTODAddressStack(SortTODSharedDeque queue) {
    super(queue);
  }

   /**
   * Sort the address on the shared stack.
   */ 
  public final void sort() {
    flushLocal();  
    ((SortTODSharedDeque)queue).sort();
  }

  /**
   * Push an address onto the address stack.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void push(VM_Address addr) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr.isZero());
    checkHeadInsert(1);
    uncheckedHeadInsert(addr);
  }

  /**
   * Pop an address from the address stack, return zero if the stack
   * is empty.
   *
   * @return The next address in the address stack, or zero if the
   * stack is empty
   */
  public final VM_Address pop() throws VM_PragmaInline {
    if (checkDequeue(1)) {
      return uncheckedDequeue();
    } else {
      return VM_Address.zero();
    }
  }

  /**
   * Check if the (local and shared) stacks are empty.
   *
   * @return True if there are no more entries on the local & shared stack,
   * false otherwise.
   */
  public final boolean isEmpty() throws VM_PragmaInline {
    return !checkDequeue(1);
  }
}
