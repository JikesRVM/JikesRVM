/*
 * (C) Copyright IBM Corp 2001,2002
 */
package org.mmtk.utility;

import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.ObjectModel;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;

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
 *
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
final class DoublyLinkedList
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
  private       Address head;
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
    head = Address.zero();   
    lock = shared ? new Lock("DoublyLinkedList") : null;
    granularity = granularity_;

    // ensure that granularity is big enough for midPayloadToNode to work
    Word tmp = Word.fromIntZeroExtend(granularity);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(tmp.and(nodeMask).EQ(tmp));
  }

  // Offsets are relative to the node (not the payload)
  //
  private static final Offset PREV_OFFSET = Offset.fromInt(0 * BYTES_IN_ADDRESS);
  private static Offset NEXT_OFFSET = Offset.fromInt(1 * BYTES_IN_ADDRESS);
  private static Offset LIST_OFFSET = Offset.fromInt(2 * BYTES_IN_ADDRESS);
  private static Offset HEADER_SIZE = Offset.fromInt(3 * BYTES_IN_ADDRESS);

  private static final Word nodeMask;
  static {
    int mask = 1;
    while (mask < HEADER_SIZE.toInt()+MAX_BYTES_PADDING) mask <<= 1;
    nodeMask = Word.fromIntZeroExtend(mask-1).not();
  }

  public final Object getOwner() {
    return owner;
  }

  static public final Object getOwner(Address node) {
    return node.loadObjectReference(LIST_OFFSET).toObject();
  }

  static public final int headerSize() throws InlinePragma {
    return HEADER_SIZE.toInt();
  }

  public final boolean isNode (Address node) {
    if (BITS_IN_ADDRESS == 64)
      return (node.toLong() / granularity * granularity) == node.toLong();
    else
      return (node.toInt() / granularity * granularity) == node.toInt();
  }

  static public final Address nodeToPayload(Address node) throws InlinePragma {
    return node.add(HEADER_SIZE);
  }

  static public final Address payloadToNode(Address payload) throws InlinePragma {
    return payload.sub(HEADER_SIZE);
  }

  static public final Address midPayloadToNode(Address payload) throws InlinePragma {
    // This method words as long as you are less than MAX_BYTES_PADDING into the payload.
    return payload.toWord().and(nodeMask).toAddress();
  }

  public final void add (Address node) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(isNode(node));
    if (lock != null) lock.acquire();
    node.store(Address.zero(), PREV_OFFSET);
    node.store(head, NEXT_OFFSET);
    node.store(ObjectReference.fromObject(owner), LIST_OFFSET);
    if (!head.isZero())
      head.store(node, PREV_OFFSET);
    head = node;
    if (lock != null) lock.release();
  }

  public final void remove (Address node) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(isNode(node));
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
    node.store(Address.zero(), LIST_OFFSET);
    if (lock != null) lock.release();
  }

  public final Address pop () throws InlinePragma {
    Address first = head;
    if (!first.isZero())
      remove(first);
    return first;
  }

  public final boolean isEmpty() throws InlinePragma {
    return head.isZero();
  }

  /**
   * Return true if a cell is on a given treadmill
   *
   * @param cell The cell being searched for
   * @param head The head of the treadmill
   * @return True if the cell is found on the treadmill
   */
  public final boolean isMember (Address node) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(isNode(node));
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

  public final void show() {
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
      driver.traceObject(cur);
      cur = cur.loadAddress(NEXT_OFFSET);
    }
  }
}
