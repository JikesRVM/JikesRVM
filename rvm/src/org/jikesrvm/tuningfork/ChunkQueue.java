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

import com.ibm.tuningfork.tracegen.chunk.RawChunk;

/**
 * A Queue of chunks intended to keep track of meta-chunks.
 * Therefore it can be implemented using Java-level synchronization and
 * allocation operations to wrap the Chunks in Queue nodes.
 */
public class ChunkQueue {

  private Node head = null;
  private Node tail = null;

  public synchronized void enqueue(RawChunk c) {
    Node newNode = new Node(c);
    if (tail == null) {
      head = newNode;
      tail = newNode;
    } else {
      tail.next = newNode;
      tail = newNode;
    }
  }

  public synchronized RawChunk dequeue() {
    if (head != null) {
      RawChunk result = head.chunk;
      head = head.next;
      if (head == null) {
        tail = null;
      }
      return result;
    } else {
      return null;
    }
  }

  public boolean isEmpty() {
    return head == null;
  }

  private static final class Node {
    final RawChunk chunk;
    Node next;
    Node(RawChunk c) { this.chunk=c; }
  };
}
