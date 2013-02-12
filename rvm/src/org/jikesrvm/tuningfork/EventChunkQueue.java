/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

package org.jikesrvm.tuningfork;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.SpinLock;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;

import com.ibm.tuningfork.tracegen.chunk.EventChunk;

/**
 * A Queue of EventChunks.
 * <p>
 * Unlike ChunkQueue, this queue is designed to be used in uninterruptible contexts.
 * It assumes that all EventChunks are NonMoving and externally kept alive for the GC;
 * therefore it can mark its head and tail fields as Untraced.
 * <p>
 * TODO: consider implementing a non-blocking queue instead of using spin locks.
 */
@Uninterruptible
public class EventChunkQueue {

  @Untraced
  private EventChunk head = null;
  @Untraced
  private EventChunk tail = null;
  private final SpinLock lock = new SpinLock();


  public void enqueue(EventChunk c) {
    if (VM.VerifyAssertions) VM._assert(c.next == null);
    lock.lock("EventChunkQueue::enqueue");
    if (tail == null) {
      head = c;
      tail = c;
    } else {
      tail.next = c;
      tail = c;
    }
    lock.unlock();
  }

  public EventChunk dequeue() {
    lock.lock("EventChunkQueue::dequeue");
    EventChunk result = head;
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
