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
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * address pairs
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class AddressPairQueue extends LocalQueue implements Constants, VM_Uninterruptible {
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
  AddressPairQueue(SharedQueue queue) {
    super(queue);
  }
 
  /**
   * Insert an address pair into the address queue.
   *
   * @param addr1 the first address to be inserted into the address queue
   * @param addr2 the second address to be inserted into the address queue
   */
  public final void insert(VM_Address addr1, VM_Address addr2) {
    if (VM.VerifyAssertions) VM._assert(!addr1.isZero());
    if (VM.VerifyAssertions) VM._assert(!addr2.isZero());
    checkInsert(2);
    uncheckedInsert(addr1.toInt());
    uncheckedInsert(addr2.toInt());
  }
 
  /**
   * Push an address pair onto the address queue.
   *
   * @param addr1 the first value to be pushed onto the address queue
   * @param addr2 the second value to be pushed onto the address queue
   */
  public final void push(VM_Address addr1, VM_Address addr2) {
    if (VM.VerifyAssertions) VM._assert(!addr1.isZero());
    if (VM.VerifyAssertions) VM._assert(!addr2.isZero());
    checkPush(2);
    uncheckedPush(addr2.toInt());
    uncheckedPush(addr1.toInt());
  }

  /**
   * Pop the first address in a pair from the address queue, return
   * zero if the queue is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final VM_Address pop1() {
     if (checkPop(2))
      return VM_Address.fromInt(uncheckedPop());
    else
      return VM_Address.zero();
  }
  
  /**
   * Pop the second address in a pair from the address queue.
   *
   * @return The next address in the address queue
   */
  public final VM_Address pop2() {
    return VM_Address.fromInt(uncheckedPop());
  }

  public final boolean isEmpty() {
    return !checkPop(2);
  }
}
