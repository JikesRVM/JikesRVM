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

package org.jikesrvm.tuningfork;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.ProcessorLock;
import org.vmmagic.pragma.Uninterruptible;

import com.ibm.tuningfork.tracegen.chunk.RawChunk;

/**
 * A Queue of chunks.
 *
 * TODO: consider implementing a non-blocking queue instead of using spin locks.
 */
@Uninterruptible
public class ChunkQueue {

  private RawChunk head = null;
  private RawChunk tail = null;
  private final ProcessorLock lock = new ProcessorLock();


  public void enqueue(RawChunk c) {
    if (VM.VerifyAssertions) VM._assert(c.next == null);
    lock.lock("chunk enqueue");
    if (tail == null) {
      head = c;
      tail = c;
    } else {
      tail.next = c;
      tail = c;
    }
    lock.unlock();
  }

  public RawChunk dequeue() {
    lock.lock("chunk dequeue");
    RawChunk result = head;
    if (head != null) {
      head = head.next;
      result.next = null;
      if (head == null) {
        if (VM.VerifyAssertions) VM._assert(tail == result);
        tail = null;
      }
    }
    lock.unlock();
    return result;
  }

  public boolean isEmpty() {
    return head == null;
  }

}
