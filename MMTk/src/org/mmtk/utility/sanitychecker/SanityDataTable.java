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
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.SimpleHashtable;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a simple hashtable to store and retrieve per
 * object information for sanity checking. <p>
 *
 * This class is not thread safe.
 */
@Uninterruptible
public final class SanityDataTable extends SimpleHashtable implements Constants {

  /** The number of bits for the normal reference count */
  private static final int NORMAL_RC_BITS = 25;

  /** The mask for the normal reference count */
  private static final int NORMAL_RC_MASK = (1 << 25) - 1;

  /** The shift for the root reference count */
  private static final int ROOT_RC_SHIFT = NORMAL_RC_BITS;

  /** The increment to use for normal increments */
  private static final int NORMAL_RC_INC = 1;

  /** The increment to use for root increments */
  private static final int ROOT_RC_INC = 1 << ROOT_RC_SHIFT;

  /**
   * Create a new data table of a specified size.
   *
   * @param rps The space to acquire the data structure from.
   * @param logSize The log of the number of table entries.
   */
  public SanityDataTable(RawPageSpace rps, int logSize) {
    super(rps, logSize, Extent.fromIntSignExtend(BYTES_IN_WORD));
  }

  /**
   * Increment the data word for an object.
   *
   * @param entry The table entry.
   * @param root True if this is a root reference.
   * @return True if this is the first ref to that object.
   */
  @Inline
  public static boolean incRC(Address entry, boolean root) {
    Address data = SimpleHashtable.getPayloadAddress(entry);
    int old = data.loadInt();
    data.store(old + (root ? ROOT_RC_INC : NORMAL_RC_INC));
    return (old == 0);
  }

  /**
   * Push any entries that are only in this table, and not the
   * passed table. This does not compare values.
   *
   * @param other The table to use for comparison.
   * @param deque The buffer to push results onto.
   */
  public void pushNotInOther(SanityDataTable other,
                             ObjectReferenceDeque deque) {
    Address entry = getFirst();
    while (!entry.isZero()) {
      Word key = SimpleHashtable.getKey(entry);
      if (!other.contains(key)) {
        deque.push(key.toAddress().toObjectReference());
      }
      entry = getNext(entry);
    }
  }


  /**
   * Given an address of an entry, read the reference count,
   * excluding root references.
   *
   * @param entry The entry
   * @return The reference count.
   */
  public static int getNormalRC(Address entry) {
    return SimpleHashtable.getPayloadAddress(entry).loadInt() & NORMAL_RC_MASK;
  }

  /**
   * Given an address of an entry, read the root reference count.
   *
   * @param entry The entry
   * @return The root reference count.
   */
  public static int getRootRC(Address entry) {
    return SimpleHashtable.getPayloadAddress(entry).loadInt() >>> ROOT_RC_SHIFT;
  }

  /**
   * Given an address of an entry, read the total reference count.
   *
   * @param entry The entry
   * @return The total reference count.
   */
  public static int getRC(Address entry) {
    int val = SimpleHashtable.getPayloadAddress(entry).loadInt();
    return (val & NORMAL_RC_MASK) + val >>> ROOT_RC_SHIFT;
  }

  /**
   * Given an address of an entry, read the reference component.
   *
   * @param entry The entry
   * @return The object reference.
   */
  public static ObjectReference getObjectReference(Address entry) {
    return SimpleHashtable.getKey(entry).toAddress().toObjectReference();
  }

  /**
   * Forward data table using the supplied trace. Note that the data is
   * not hashed correctly, so only enumeration can be used without
   * rehashing.
   *
   * @param trace The trace to use.
   */
  public void forwardTable(TraceLocal trace) {
    Address entry = getFirst();
    while (!entry.isZero()) {
      ObjectReference obj = getObjectReference(entry);
      SimpleHashtable.replaceKey(entry, trace.getForwardedReference(obj).toAddress().toWord());
      entry = getNext(entry);
    }
  }

  /**
   * Get an entry for an object.
   *
   * @param object The object to find an entry for.
   * @param create Create an entry if none exists?
   * @return The entry address.
   */
  public Address getEntry(ObjectReference object, boolean create) {
    return super.getEntry(object.toAddress().toWord(), create);
  }
}
