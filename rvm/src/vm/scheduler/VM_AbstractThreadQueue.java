/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Base class for queues of VM_Threads.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
abstract class VM_AbstractThreadQueue implements Uninterruptible {

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
  abstract void    dump() throws InterruptiblePragma;
}
