/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
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

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local ssb will append
   * its buffers (when full or flushed).
   */
  WriteBuffer(SharedQueue queue) {
    super(queue);
  }

  /**
   * Insert a value to be remembered into the write buffer.
   *
   * @param addr the value to be inserted into the write buffer
   */
  public final void insert(VM_Address addr) throws VM_PragmaNoInline {
//      if (inWB) {
//        VM.sysWrite("oops!\n");
//        VM_Scheduler.dumpStack();
//    if (addr.EQ(VM_Address.fromInt(0x4193fb30)))
//     if (addr.EQ(VM_Address.fromInt(0x4193fc20)))
//       VM_Scheduler.dumpStack(8, true);
//      }
//      inWB = true;
//     VM.sysWrite("(");
//     VM.sysWrite(addr);
    checkInsert(1);
//     VM.sysWrite(".");
     uncheckedInsert(addr.toInt());
//     VM.sysWrite(addr); VM.sysWrite(" "); VM.sysWrite(VM_Magic.getThreadId()); VM.sysWrite(" i\n");
//     VM.sysWrite(")");
//      inWB = false;
  }
  private static boolean inWB = false;
}
