/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of addresses
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class AddressQueue extends LocalQueue implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$"; 
 
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  AddressQueue(SharedQueue queue) {
    super(queue);
  }

  /**
   * Insert an address into the address queue.
   *
   * @param addr the address to be inserted into the address queue
   */
  public final void insert(VM_Address addr) {
    if (VM.VerifyAssertions) VM._assert(!addr.isZero());
    checkInsert(1);
    uncheckedInsert(addr.toInt());
  }

  /**
   * Push an address onto the address queue.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void push(VM_Address addr) {
    if (VM.VerifyAssertions) VM._assert(!addr.isZero());
    checkPush(1);
    uncheckedPush(addr.toInt());
  }

  /**
   * Pop an address from the address queue, return zero if the queue
   * is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final VM_Address pop() {
    if (checkPop(1))
      return VM_Address.fromInt(uncheckedPop());
    else
      return VM_Address.zero();
  }
}
