/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Magic;
/**
 * This supports <i>unsynchronized</i> insertion of write buffer values.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
class WriteBuffer extends LocalSSB implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local ssb will append
   * its buffers (when full or flushed).
   */
  WriteBuffer(SharedDeque queue) {
    super(queue);
  }

  /**
   * Insert a value to be remembered into the write buffer.
   *
   * @param addr the value to be inserted into the write buffer
   */
  public final void insert(VM_Address addr)
    throws VM_PragmaNoInline {
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }
  private static boolean inWB = false;
}
