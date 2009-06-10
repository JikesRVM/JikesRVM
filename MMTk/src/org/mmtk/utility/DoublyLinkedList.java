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
package org.mmtk.utility;

import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * FIXME This class must be re-written as it makes the assumption that
 * the implementation language (Java) and the language being
 * implemented are the same.  This is true in the case of Jikes RVM,
 * but it is not true for any VM implementing a language other than
 * Java.
 *
 *
 * Each instance of this class is a doubly-linked list, in which
 * each item or node is a piece of memory.  The first two words of each node
 * contains the forward and backward links.  The third word contains
 * the treadmill.  The remaining portion is the payload.
 *
 * The treadmill object itself must not be moved.
 *
 * Access to the instances may be synchronized depending on the
 * constructor argument.
 */
@Uninterruptible public final class DoublyLinkedList implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private Address head;
  private final Lock lock;
  private final int logGranularity;  // Each node on the treadmill is guaranteed to be a multiple of granularity.

  /****************************************************************************
   *
   * Instance Methods
   */

  /**
   * Constructor
   */
  public DoublyLinkedList(int logGranularity, boolean shared) {
    head = Address.zero();
    lock = shared ? VM.newLock("DoublyLinkedList") : null;
    this.logGranularity = logGranularity;

    // ensure that granularity is big enough for midPayloadToNode to work
    Word tmp = Word.one().lsh(logGranularity);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tmp.and(nodeMask).EQ(tmp));
  }

  // Offsets are relative to the node (not the payload)
  //
  private static final Offset PREV_OFFSET = Offset.fromIntSignExtend(0 * BYTES_IN_ADDRESS);
  private static Offset NEXT_OFFSET = Offset.fromIntSignExtend(1 * BYTES_IN_ADDRESS);
  private static Offset HEADER_SIZE = Offset.fromIntSignExtend(2 * BYTES_IN_ADDRESS);

  private static final Word nodeMask;
  static {
    Word mask = Word.one();
    while (mask.LE(HEADER_SIZE.plus(MAX_BYTES_PADDING).toWord())) mask = mask.lsh(1);
    nodeMask = mask.minus(Word.one()).not();
  }

  @Inline
  public static int headerSize() {
    return HEADER_SIZE.toInt();
  }

  public boolean isNode(Address node) {
    return node.toWord().rshl(logGranularity).lsh(logGranularity).EQ(node.toWord());
  }

  @Inline
  public static Address nodeToPayload(Address node) {
    return node.plus(HEADER_SIZE);
  }

  @Inline
  public static Address midPayloadToNode(Address payload) {
    // This method words as long as you are less than MAX_BYTES_PADDING into the payload.
    return payload.toWord().and(nodeMask).toAddress();
  }

  @Inline
  public void add(Address node) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isNode(node));
    if (lock != null) lock.acquire();
    node.store(Address.zero(), PREV_OFFSET);
    node.store(head, NEXT_OFFSET);
    if (!head.isZero())
      head.store(node, PREV_OFFSET);
    head = node;
    if (lock != null) lock.release();
  }

  @Inline
  public void remove(Address node) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isNode(node));
    if (lock != null) lock.acquire();
    Address prev = node.loadAddress(PREV_OFFSET);
    Address next = node.loadAddress(NEXT_OFFSET);
    // Splice the node out of the list
    if (!next.isZero())
      next.store(prev, PREV_OFFSET);
    if (prev.isZero())
      head = next;
    else
      prev.store(next, NEXT_OFFSET);
    // Null out node's reference to the list
    node.store(Address.zero(), PREV_OFFSET);
    node.store(Address.zero(), NEXT_OFFSET);
    if (lock != null) lock.release();
  }

  @Inline
  public Address getHead() {
    return head;
  }

  @Inline
  public Address getNext(Address node) {
    return node.loadAddress(NEXT_OFFSET);
  }

  @Inline
  public Address pop() {
    Address first = head;
    if (!first.isZero())
      remove(first);
    return first;
  }

  @Inline
  public boolean isEmpty() {
    return head.isZero();
  }

  /**
   * Return true if a cell is on a given treadmill
   *
   * @param node The cell being searched for
   * @return True if the cell is found on the treadmill
   */
  public boolean isMember(Address node) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isNode(node));
    boolean result = false;
    if (lock != null) lock.acquire();
    Address cur = head;
    while (!cur.isZero()) {
      if (cur.EQ(node)) {
        result = true;
        break;
      }
      cur = cur.loadAddress(NEXT_OFFSET);
    }
    if (lock != null) lock.release();
    return result;
  }

  public void show() {
    if (lock != null) lock.acquire();
    Address cur = head;
    Log.write(cur);
    while (!cur.isZero()) {
      cur = cur.loadAddress(NEXT_OFFSET);
      Log.write(" -> "); Log.write(cur);
    }
    Log.writeln();
    if (lock != null) lock.release();
  }


  /**
   * Gather data for GCSpy
   * @param driver the GCSpy space driver
   */
  void gcspyGatherData(AbstractDriver driver) {
    // GCSpy doesn't need a lock (in its stop the world config)
    Address cur = head;
    while (!cur.isZero()) {
      driver.scan(cur);
      cur = cur.loadAddress(NEXT_OFFSET);
    }
  }
}
