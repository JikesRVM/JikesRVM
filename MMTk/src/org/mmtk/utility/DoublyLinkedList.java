/*
 * (C) Copyright IBM Corp 2001,2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
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
final class DoublyLinkedList
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
  private       VM_Address head;
  private final Lock lock;
  private final Object owner;
  private final int granularity;  // Each node on the treadmill is guaranteed to be a multiple of this.

  /****************************************************************************
   *
   * Instance Methods
   */

  /**
   * Constructor
   */
  DoublyLinkedList (int granularity_, boolean shared, Object owner_) {
    owner = owner_;
    head = VM_Address.zero();   
    lock = shared ? new Lock("DoublyLinkedList") : null;
    granularity = granularity_; 
  }

  // Offsets are relative to the node (not the payload)
  //
  private static int PREV_OFFSET = 0 * BYTES_IN_ADDRESS;
  private static int NEXT_OFFSET = 1 * BYTES_IN_ADDRESS;
  private static int LIST_OFFSET = 2 * BYTES_IN_ADDRESS;
  private static int HEADER_SIZE = 3 * BYTES_IN_ADDRESS;

  public final Object getOwner() {
    return owner;
  }

  static public final Object getOwner (VM_Address node) {
    return VM_Magic.addressAsObject(VM_Magic.getMemoryAddress(node.add(LIST_OFFSET)));
  }

  static public final int headerSize() throws VM_PragmaInline {
    return HEADER_SIZE;
  }

  public final boolean isNode (VM_Address node) {
    VM_Word n = node.toWord();
    return (n.toInt() / granularity * granularity) == n.toInt();
  } 

  static public final VM_Address nodeToPayload(VM_Address node) throws VM_PragmaInline {
    return node.add(HEADER_SIZE);
  }

  static public final VM_Address payloadToNode(VM_Address payload) throws VM_PragmaInline {
    return payload.sub(HEADER_SIZE);
  }

  public final void add (VM_Address node) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(isNode(node));
    if (lock != null) lock.acquire();
    VM_Magic.setMemoryAddress(node.add(PREV_OFFSET), VM_Address.zero());
    VM_Magic.setMemoryAddress(node.add(NEXT_OFFSET), head);
    VM_Magic.setMemoryAddress(node.add(LIST_OFFSET), VM_Magic.objectAsAddress(owner));
    if (!head.isZero())
      VM_Magic.setMemoryAddress(head.add(PREV_OFFSET), node);
    head = node;
    if (lock != null) lock.release();
  }

  public final void remove (VM_Address node) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(isNode(node));
    if (lock != null) lock.acquire();
    VM_Address prev = VM_Magic.getMemoryAddress(node.add(PREV_OFFSET));
    VM_Address next = VM_Magic.getMemoryAddress(node.add(NEXT_OFFSET));
    // Splice the node out of the list
    if (!next.isZero()) 
        VM_Magic.setMemoryAddress(next.add(PREV_OFFSET), prev);
    if (prev.isZero()) 
        head = next;
    else
        VM_Magic.setMemoryAddress(prev.add(NEXT_OFFSET), next);
    // Null out node's reference to the list
    VM_Magic.setMemoryAddress(node.add(PREV_OFFSET), VM_Address.zero());
    VM_Magic.setMemoryAddress(node.add(NEXT_OFFSET), VM_Address.zero());
    VM_Magic.setMemoryAddress(node.add(LIST_OFFSET), VM_Address.zero());
    if (lock != null) lock.release();
  }

  public final VM_Address pop () throws VM_PragmaInline {
    VM_Address first = head;
    if (!first.isZero())
      remove(first);
    return first;
  }

  public final boolean isEmpty() throws VM_PragmaInline {
    return head.isZero();
  }

  /**
   * Return true if a cell is on a given treadmill
   *
   * @param cell The cell being searched for
   * @param head The head of the treadmill
   * @return True if the cell is found on the treadmill
   */
  public final boolean isMember (VM_Address node) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(isNode(node));
    boolean result = false;
    if (lock != null) lock.acquire();
    VM_Address cur = head;
    while (!cur.isZero()) {
      if (cur.EQ(node)) {
        result = true;
        break;
     }
     cur = VM_Magic.getMemoryAddress(cur.add(NEXT_OFFSET));
    }
    if (lock != null) lock.release();
    return result;
  }

  public final void show() {
    if (lock != null) lock.acquire();
    VM_Address cur = head;
    Log.write(cur);
    while (!cur.isZero()) {
      cur =      cur = VM_Magic.getMemoryAddress(cur.add(NEXT_OFFSET));
      Log.write(" -> "); Log.write(cur);
    }
    Log.writeln();
    if (lock != null) lock.release();
  }


  //-if RVM_WITH_GCSPY
  /**
   * Gather data for GCSpy
   * @param gcspyDriver the GCSpy space driver
   */
  void gcspyGatherData(TreadmillDriver tmDriver) {
    // GCSpy doesn't need a lock (in its stop the world config)
    VM_Address cur = head;
    while (!cur.isZero()) {
      tmDriver.traceObject(cur);
      cur = VM_Magic.getMemoryAddress(cur.add(NEXT_OFFSET));
    }
  }
  //-endif


}
