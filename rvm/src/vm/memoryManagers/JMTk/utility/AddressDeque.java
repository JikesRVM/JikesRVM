/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of addresses
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
import org.mmtk.vm.VM_Interface;
public class AddressDeque extends LocalDeque 
  implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$"; 
 
  /****************************************************************************
   *
   * Public instance methods
   */
  public final String name;

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public AddressDeque(String n, SharedDeque queue) {
    super(queue);
    name = n;
  }

  /**
   * Insert an address into the address queue.
   *
   * @param addr the address to be inserted into the address queue
   */
  public final void insert(VM_Address addr) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr.isZero());
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }

  /**
   * Push an address onto the address queue.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void push(VM_Address addr) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!addr.isZero());
    checkHeadInsert(1);
    uncheckedHeadInsert(addr);
  }

  /**
   * Push an address onto the address queue, force this out of line
   * ("OOL"), in some circumstnaces it is too expensive to have the
   * push inlined, so this call is made.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void pushOOL(VM_Address addr) throws VM_PragmaNoInline {
    push(addr);
  }

  /**
   * Pop an address from the address queue, return zero if the queue
   * is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final VM_Address pop() throws VM_PragmaInline {
    if (checkDequeue(1)) {
      return uncheckedDequeue();
    }
    else {
      return VM_Address.zero();
    }
  }

  public final boolean isEmpty() throws VM_PragmaInline {
    return !checkDequeue(1);
  }

  public final boolean isNonEmpty() throws VM_PragmaInline {
    return checkDequeue(1);
  }

}
