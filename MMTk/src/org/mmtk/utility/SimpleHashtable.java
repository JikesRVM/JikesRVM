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

import org.mmtk.policy.RawPageSpace;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a simple hashtable. It is intended for use
 * in sanity checking or debugging, not high-performance algorithms.<p>
 *
 * This class is not thread safe.
 */
@Uninterruptible public abstract class SimpleHashtable implements Constants {
  /** The number of low order bits to ignore */
  private static final int HASH_SHIFT = 3;

  /** Offset to the key */
  private static final Offset KEY_OFFSET = Offset.zero();

  /** Offset to the data */
  private static final Offset DATA_OFFSET = Offset.fromIntSignExtend(BYTES_IN_WORD);

  /** The size of each entry in the table */
  private final Extent entrySize;

  /** The mask to use to get the hash code */
  private final Word mask;

  /** The start address of the data table */
  private Address base;

  /** The full size of the table */
  private final Extent size;

  /** The space to use for allocating the data structure */
  private final RawPageSpace space;

  /** Is this table valid (created) */
  private boolean valid;

  /**
   * Create a new data table of a specified size.
   *
   * @param rps The space to acquire the data structure from.
   * @param logSize The log of the number of table entries.
   * @param es The size of each entry.
   */
  protected SimpleHashtable(RawPageSpace rps, int logSize, Extent es) {
    mask = Word.fromIntZeroExtend((1 << logSize) - 1);
    entrySize = es.plus(BYTES_IN_WORD);
    size = Extent.fromIntZeroExtend((1 << logSize) * entrySize.toInt());
    base = Address.zero();
    space = rps;
    valid = false;
  }

  /**
   * Create a (zeroed) table.
   */
  public final void acquireTable() {
    base = space.acquire(Conversions.bytesToPages(size));
    VM.memory.zero(false, base, size);
    valid = true;
  }

  /**
   * Drop the table (after collection).
   */
  public final void releaseTable() {
    space.release(base);
    valid = false;
  }

  /**
   * @return True if this table has backing data and is ready for use.
   */
  public final boolean isValid() {
    return valid;
  }

  /**
   * Retrieve a pointer to the entry for the given object, or zero if one
   * does not exist, unless create is passed.<p>
   *
   * If create is true, the return is guaranteed to be non-null.
   *
   * @param key The key used to lookup.
   * @param create Create a new entry if not found.
   * @return A pointer to the reference or null.
   */
  @Inline
  public final Address getEntry(Word key, boolean create) {
    int startIndex = computeHash(key);
    int index = startIndex;
    Word curAddress;
    Address entry;
    do {
      entry = getEntry(index);
      curAddress = entry.loadWord(KEY_OFFSET);
      index = (index + 1) & mask.toInt();
    } while(curAddress.NE(key) &&
            !curAddress.isZero() &&
            index != startIndex);

    if (index == startIndex) {
      VM.assertions.fail("No room left in table!");
    }

    if (curAddress.isZero()) {
      if (!create) return Address.zero();
      entry.store(key, KEY_OFFSET);
    }

    return entry;
  }

  /**
   * Compute the hashtable index for a given object.
   *
   * @param key The key.
   * @return The index.
   */
  @Inline
  private int computeHash(Word key) {
    return key.rshl(HASH_SHIFT).and(mask).toInt();
  }

  /**
   * Return the address of a specified entry in the table.
   *
   * @param index The index of the entry.
   * @return An address to the entry.
   */
  @Inline
  private Address getEntry(int index) {
    return base.plus(Extent.fromIntZeroExtend(index * entrySize.toInt()));
  }

  /**
   * Does the passed object have an entry in the table?
   *
   * @param key The key to find an entry for
   * @return True if there is an entry for that object.
   */
  public final boolean contains(Word key) {
    return !getEntry(key, false).isZero();
  }

  /**
   * @return The first non-zero element in the table, or null if
   * the table is empty.
   */
  public final Address getFirst() {
    return getNext(base.minus(entrySize));
  }

  /**
   * The next element in the table after the passed entry, or
   * null if it is the last entry.
   *
   * @param curr The object to look for the next entry from.
   * @return The next entry or null.
   */
  public final Address getNext(Address curr) {
    Address entry = curr.plus(entrySize);
    while (entry.LT(base.plus(size))) {
      if (!entry.loadWord().isZero()) return entry;
      entry = entry.plus(entrySize);
    }
    return Address.zero();
  }

  /**
   * Given an address of an entry, return a pointer to the payload.
   *
   * @param entry The entry
   * @return The object reference.
   */
  public static Address getPayloadAddress(Address entry) {
    return entry.plus(DATA_OFFSET);
  }

  /**
   * Given a key, return a pointer to the payload.
   *
   * @param key The key
   * @return The object reference.
   */
  public final Address getPayloadAddress(Word key) {
    Address entry = getEntry(key, false);
    if (entry.isZero()) return Address.zero();

    return entry.plus(DATA_OFFSET);
  }


  /**
   * Return the key for a given entry.
   *
   * @param entry The entry.
   * @return The key.
   */
  public static Word getKey(Address entry) {
    return entry.loadWord(KEY_OFFSET);
  }

  /**
   * Update the key for a given entry. This operation is not
   * safe without rehashing
   *
   * @param entry The entry to update.
   * @param key The new key.
   */
  public static void replaceKey(Address entry, Word key) {
    entry.store(key, KEY_OFFSET);
  }

}
