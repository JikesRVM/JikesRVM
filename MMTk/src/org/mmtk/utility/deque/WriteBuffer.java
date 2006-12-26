/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> insertion of write buffer values.
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public class WriteBuffer extends LocalSSB
  implements Constants {
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
  @NoInline
  public final void insert(Address addr) { 
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }
}
