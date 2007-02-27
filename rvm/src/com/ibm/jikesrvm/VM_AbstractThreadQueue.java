/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm;

import org.vmmagic.pragma.*;

/**
 * Base class for queues of VM_Threads.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
@Uninterruptible abstract class VM_AbstractThreadQueue {

  // Are any threads on the queue?
  //
  abstract boolean isEmpty ();

  // Add a thread to tail of queue.
  //
  abstract void enqueue (VM_Thread t);

  // Remove thread from head of queue.
  // Returned: the thread (null --> queue is empty)
  //
  abstract VM_Thread dequeue ();
  
  // Number of items on queue (an estimate: queue is not locked during the scan).
  //
  abstract int length();

  // For debugging.
  //
  abstract boolean contains(VM_Thread t);
  @Interruptible
  abstract void    dump(); 
}
