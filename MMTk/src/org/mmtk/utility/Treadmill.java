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

import org.mmtk.utility.gcspy.drivers.TreadmillDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * FIXME The DoublyLinkedList class, upon which this depends, must be
 * re-written as it makes the assumption that the implementation
 * language (Java) and the language being implemented are the same.
 * This is true in the case of Jikes RVM, but it is not true for any
 * VM implementing a language other than Java.<p>
 *
 * Each instance of this class is a doubly-linked list, in which
 * each item or node is a piece of memory.  The first two words of each node
 * contains the forward and backward links.  The third word contains
 * the treadmill.  The remaining portion is the payload.<p>
 *
 * The treadmill object itself must not be moved.<p>
 *
 * Access to the instances may be synchronized depending on the constructor argument.
 */
@Uninterruptible
public final class Treadmill {

  /****************************************************************************
   *
   * Instance variables
   */

  /**
   *
   */
  private DoublyLinkedList fromSpace;
  private DoublyLinkedList toSpace;
  private DoublyLinkedList collectNursery;
  private DoublyLinkedList allocNursery;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * @param granularity TODO needs documentation
   * @param shared <code>true</code> if the created instance will be shared between threads. If it is shared, accesses will be synchronized using locks.
   */
  public Treadmill(int granularity, boolean shared) {
    fromSpace = new DoublyLinkedList(granularity, shared);
    toSpace = new DoublyLinkedList(granularity, shared);
    allocNursery = new DoublyLinkedList(granularity, shared);
    collectNursery = new DoublyLinkedList(granularity, shared);
  }

  /**
   * Adds a node to the treadmill. This is usually performed on allocation.
   *
   * @param node the node to add
   * @param nursery whether to add to the nursery or to the to-space
   */
  @Inline
  public void addToTreadmill(Address node, boolean nursery) {
    if (nursery)
      allocNursery.add(node);
    else
      toSpace.add(node);
  }

  /**
   * Removes a node from the nursery list.
   *
   * @return the removed node
   */
  @Inline
  public Address popNursery() {
    return collectNursery.pop();
  }

  /**
   * Removes a node from the mature list.
   *
   * @return the removed node
   */
  @Inline
  public Address pop() {
    return fromSpace.pop();
  }

  /**
   * Copies a node (during gc tracing).
   *
   * @param node the node to copy
   * @param isInNursery whether the node is in the nursery or the
   *  from-space
   */
  @Inline
  public void copy(Address node, boolean isInNursery) {
    if (isInNursery) {
      collectNursery.remove(node);
    } else {
      fromSpace.remove(node);
    }
    toSpace.add(node);
  }

  /**
   * @return whether the to-space is empty
   */
  @Inline
  public boolean toSpaceEmpty() {
    return toSpace.isEmpty();
  }

  /**
   * @return whether the from-space is empty
   */
  @Inline
  public boolean fromSpaceEmpty() {
    return fromSpace.isEmpty();
  }

  /**
   * @return whether the nursery is empty
   */
  @Inline
  public boolean nurseryEmpty() {
    return collectNursery.isEmpty();
  }

  /**
   * Flips the roles of the spaces in preparation for a collection.
   *
   * @param fullHeap whether the collection is full heap
   */
  public void flip(boolean fullHeap) {
    DoublyLinkedList tmp = allocNursery;
    allocNursery = collectNursery;
    collectNursery = tmp;
    if (fullHeap) {
      tmp = fromSpace;
      fromSpace = toSpace;
      toSpace = tmp;
    }
  }

  /****************************************************************************
   *
   * Misc header manipulation
   */

  /**
   * @return the header size
   */
  @Inline
  public static int headerSize() {
    return DoublyLinkedList.headerSize();
  }

  @Inline
  public static Address nodeToPayload(Address payload) {
    return DoublyLinkedList.nodeToPayload(payload);
  }

  @Inline
  public static Address midPayloadToNode(Address payload) {
    return DoublyLinkedList.midPayloadToNode(payload);
  }

  /****************************************************************************
   *
   * GCSpy
   */

  /**
   * Gather data for GCSpy from the nursery
   * @param event the gc event
   * @param tmDriver the GCSpy space driver
   */
  public void gcspyGatherData(int event, TreadmillDriver tmDriver) {
    this.allocNursery.gcspyGatherData(tmDriver);
  }

  /**
   * Gather data for GCSpy
   * @param event the gc event
   * @param tmDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver tmDriver, boolean tospace) {
    if (tospace)
      toSpace.gcspyGatherData(tmDriver);
    else
      fromSpace.gcspyGatherData(tmDriver);
  }
}
