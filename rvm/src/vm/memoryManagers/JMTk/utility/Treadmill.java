/*
 * (C) Copyright IBM Corp 2001,2002
 */
package org.mmtk.utility;

import org.mmtk.utility.gcspy.TreadmillDriver;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
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
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private DoublyLinkedList fromSpace;
  private DoublyLinkedList toSpace;

  /****************************************************************************
   *
   * Instance Methods
   */

  /**
   * Constructor
   */
  public Treadmill (int granularity, boolean shared) {
    fromSpace = new DoublyLinkedList (granularity, shared, this); 
    toSpace = new DoublyLinkedList (granularity, shared, this); 
  }

  static public final Treadmill getTreadmill (Address node) {
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

  public final void addToFromSpace (Address node) throws InlinePragma {
    fromSpace.add(node);
  }

  public final Address popFromSpace () throws InlinePragma {
    return fromSpace.pop();
  }

  public final void copy (Address node) throws InlinePragma { 
    fromSpace.remove(node);
    toSpace.add(node);
  }

  public final boolean toSpaceEmpty () throws InlinePragma {
    return toSpace.isEmpty();
  }

  public final void flip() {  
    DoublyLinkedList tmp = fromSpace;
    fromSpace = toSpace;
    toSpace = tmp;
  }

  /**
   * Gather data for GCSpy
   * @param event the gc event
   * @param gcspyDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver tmDriver, boolean tospace) {
    if (tospace) 
      toSpace.gcspyGatherData(tmDriver);
    else
      fromSpace.gcspyGatherData(tmDriver);
  }

}
