/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler;

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Base class for queues of VM_Threads.
 */
@Uninterruptible
abstract class VM_AbstractThreadQueue {

  // Are any threads on the queue?
  //
  abstract boolean isEmpty();

  // Add a thread to tail of queue.
  //
  abstract void enqueue(VM_Thread t);

  // Remove thread from head of queue.
  // Returned: the thread (null --> queue is empty)
  //
  abstract VM_Thread dequeue();

  // Number of items on queue (an estimate: queue is not locked during the scan).
  //
  abstract int length();

  // For debugging.
  //
  abstract boolean contains(VM_Thread t);

  @Interruptible
  abstract void dump();
}
