/*
 * (C) Copyright IBM Corp 2001,2002
 */
package org.mmtk.utility;

import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * FIXME The DoublyLinkedList class, upon which this depends, must be
 * re-written as it makes the assumption that the implementation
 * language (Java) and the language being implemented are the same.
 * This is true in the case of Jikes RVM, but it is not true for any
 * VM implementing a language other than Java.
 *
 * Each instance of this class is a doubly-linked list, in which
 * each item or node is a piece of memory.  The first two words of each node
 * contains the forward and backward links.  The third word contains
 * the treadmill.  The remaining portion is the payload.
 * 
 * The treadmill object itself must not be moved.
 * 
 * Access to the instances may be synchronized depending on the constructor argument.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public final class Treadmill
  implements Constants, Uninterruptible {

  /****************************************************************************
   * 
   * Instance variables
   */
  private DoublyLinkedList fromSpace;
  private DoublyLinkedList toSpace;
  private DoublyLinkedList nursery;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public Treadmill(int granularity, boolean shared) {
    fromSpace = new DoublyLinkedList(granularity, shared, this);
    toSpace = new DoublyLinkedList(granularity, shared, this);
    nursery = new DoublyLinkedList(granularity, shared, this);
  }

  public final void addToTreadmill(Address node) throws InlinePragma {
    nursery.add(node);
  }

  public final Address pop(boolean fromNursery) throws InlinePragma {
    return (fromNursery) ? nursery.pop() : fromSpace.pop();
  }

  public final void copy(Address node, boolean isInNursery) throws InlinePragma {
    if (isInNursery) 
      nursery.remove(node);
    else
      fromSpace.remove(node);
    toSpace.add(node);
  }

  public final boolean toSpaceEmpty() throws InlinePragma {
    return toSpace.isEmpty();
  }
  
  public final boolean fromSpaceEmpty() throws InlinePragma {
    return fromSpace.isEmpty();
  }
  
  public final boolean nurseryEmpty() throws InlinePragma {
    return nursery.isEmpty();
  }
  
  public final void flip() {
    DoublyLinkedList tmp = fromSpace;
    fromSpace = toSpace;
    toSpace = tmp;
  }

  /****************************************************************************
   * 
   * Misc header manipulation
   */
  
  static public final Treadmill getTreadmill(Address node) {
    return (Treadmill) DoublyLinkedList.getOwner(node);
  }

  static public final int headerSize() throws InlinePragma {
    return DoublyLinkedList.headerSize();
  }

  static public final Address nodeToPayload(Address payload) throws InlinePragma {
    return DoublyLinkedList.nodeToPayload(payload);
  }

  static public final Address payloadToNode(Address payload) throws InlinePragma {
    return DoublyLinkedList.payloadToNode(payload);
  }

  static public final Address midPayloadToNode(Address payload) throws InlinePragma {
    return DoublyLinkedList.midPayloadToNode(payload);
  }

  /****************************************************************************
   * 
   * GCSpy
   */

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
