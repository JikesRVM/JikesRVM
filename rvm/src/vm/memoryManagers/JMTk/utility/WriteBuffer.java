/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> insertion of write buffer values.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class WriteBuffer extends LocalSSB
  implements Constants, Uninterruptible {
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
  public WriteBuffer(SharedDeque queue) {
    super(queue);
  }

  /**
   * Insert a value to be remembered into the write buffer.
   *
   * @param addr the value to be inserted into the write buffer
   */
  public final void insert(Address addr)
    throws NoInlinePragma {
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }
  private static boolean inWB = false;
}
