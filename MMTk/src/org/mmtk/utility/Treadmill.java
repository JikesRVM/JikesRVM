/*
 * (C) Copyright IBM Corp 2001,2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

//-if RVM_WITH_GCSPY
import com.ibm.JikesRVM.memoryManagers.JMTk.TreadmillDriver;
//-endif

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
final class Treadmill
  implements Constants, VM_Uninterruptible {
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
  Treadmill (int granularity, boolean shared) {
    fromSpace = new DoublyLinkedList (granularity, shared, this); 
    toSpace = new DoublyLinkedList (granularity, shared, this); 
  }

  static public final Treadmill getTreadmill (VM_Address node) {
    return (Treadmill) DoublyLinkedList.getOwner(node);
  }

  static public final int headerSize() throws VM_PragmaInline {
    return DoublyLinkedList.headerSize();
  }

  static public final VM_Address nodeToPayload(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.nodeToPayload(payload);
  }

  static public final VM_Address payloadToNode(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.payloadToNode(payload);
  }

  static public final VM_Address midPayloadToNode(VM_Address payload) throws VM_PragmaInline {
    return DoublyLinkedList.midPayloadToNode(payload);
  }

  public final void addToFromSpace (VM_Address node) throws VM_PragmaInline {
    fromSpace.add(node);
  }

  public final VM_Address popFromSpace () throws VM_PragmaInline {
    return fromSpace.pop();
  }

  public final void copy (VM_Address node) throws VM_PragmaInline { 
    fromSpace.remove(node);
    toSpace.add(node);
  }

  public final boolean toSpaceEmpty () throws VM_PragmaInline {
    return toSpace.isEmpty();
  }

  public final void flip() {  
    DoublyLinkedList tmp = fromSpace;
    fromSpace = toSpace;
    toSpace = tmp;
  }

  //-if RVM_WITH_GCSPY
  /**
   * Gather data for GCSpy
   * @param event the gc event
   * @param gcspyDriver the GCSpy space driver
   */
  void gcspyGatherData(int event, TreadmillDriver tmDriver) {
    // for now, let's look through both spaces
    // we might want to return something to help resize the GCSpy space
    fromSpace.gcspyGatherData(tmDriver);
    toSpace.gcspyGatherData(tmDriver);
  }
  //-endif

}
