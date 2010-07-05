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
package org.vmmagic.unboxed.harness;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.vmmagic.unboxed.Address;

import static org.vmmagic.unboxed.harness.MemoryConstants.*;
/**
 * Represents a single page of memory.
 */
final class MemoryPage {
  /** Is this page currently readable */
  boolean readable;
  /** The base address of this page */
  private final Address pageAddress;
  /** The raw data on this page */
  private final int[] data;
  /** Watched indexes */
  private final boolean[] watch;

  /** Mask of index into page */
  private static final int INDEX_MASK = (BYTES_IN_PAGE - 1);

  /** log_2 of bytes in a memory cell */
  private static final int LOG_BYTES_IN_CELL = LOG_BYTES_IN_INT;
  /** Bytes in a memory cell */
  private static final int BYTES_IN_CELL = BYTES_IN_INT;
  /** Dimensions of memory cells (the contents of a memory page) */
  private static final int CELL_MASK = INT_MASK;

  /**
   * Create a new MemoryPage based on the given address
   */
  MemoryPage(Address pageAddress) {
    this.pageAddress = pageAddress;
    this.readable = true;
    this.data = new int[BYTES_IN_PAGE >>> LOG_BYTES_IN_CELL];
    this.watch = getWatchPoints();
    if (Trace.isEnabled(Item.MEMORY)) {
      Object[] args = { pageAddress };
      Trace.trace(Item.MEMORY,"Mapping page %s%n", args);
    }
  }

  /**
   * The base address of a given cell
   * @param index
   * @return
   */
  private Address cellAddress(int index) {
    return pageAddress.plus(index<<LOG_BYTES_IN_CELL);
  }

  /**
   * Zero the memory in this page.
   */
  public void zero() {
    for(int i=0; i < data.length; i++) {
      write(i, 0);
    }
  }



  /**
   * Construct a long value from 2 ints (high and low order 32-bit words)
   * @param high High 32-bits of result
   * @param low Low 32-bits of result
   * @return
   */
  @SuppressWarnings("cast") // Make cast explicit, because oddness can happen
  private long longFrom2Ints(int high, int low) {
    return (((long)high) << 32) |(((long)low & 0xFFFFFFFFL));
  }

  /**
   * Calculate the index to use in the page data based on the given address.
   * @param address The address being used.
   * @return The index into the page data.
   */
  private int getIndex(Address address) {
    assert SimulatedMemory.onSamePage(address,pageAddress) :
      "Invalid access of " + address + " in page " + pageAddress;
    return ((int)((address.toLong()) & INDEX_MASK)) >>> LOG_BYTES_IN_CELL;
  }

  /**
   * Load a byte value from this page.
   * @param address Address of byte to return
   * @return The contents of the byte at the address
   */
  public byte getByte(Address address) {
    int bitShift = ((address.toInt()) & ~CELL_MASK) << MemoryConstants.LOG_BITS_IN_BYTE;
    int index = getIndex(address);
    return (byte)(read(index) >>> bitShift);
  }

  /**
   * Load a char value from this page.
   * @param address Address of char to return
   * @return The contents of the char at the address
   */
  public char getChar(Address address) {
    int bitShift = ((address.toInt()) & ~CELL_MASK) << MemoryConstants.LOG_BITS_IN_BYTE;
    assert bitShift == 0 || bitShift == 16: "misaligned char access at "+address;
    int index = getIndex(address);
    return (char)(read(index) >>> bitShift);
  }

  /**
   * Load an integer value from this page.
   * @param address Address of int to return
   * @return The contents of the int at the address
   */
  public int getInt(Address address) {
    assert ((address.toInt()) % MemoryConstants.BYTES_IN_INT) == 0: "misaligned 4b access at "+address;
    return read(getIndex(address));
  }

  /**
   * Load a long value from this page.
   * @param address Address of long to return
   * @return The contents of the long at the address
   */
  public long getLong(Address address) {
    if (SimulatedMemory.ALIGN_CHECK_LONG) {
      assert ((address.toLong()) % MemoryConstants.BYTES_IN_LONG) == 0: "misaligned 8b access at "+address;
    }
    return longFrom2Ints(getInt(address.plus(BYTES_IN_CELL)),getInt(address));
  }

  @SuppressWarnings("cast")
  public synchronized byte setByte(Address address, byte value) {
    int shift = ((address.toInt()) & ~MemoryConstants.INT_MASK) << MemoryConstants.LOG_BITS_IN_BYTE;
    int mask = 0x000000FF << shift;
    int newValue = (((int)value) << shift) & mask;
    int index = getIndex(address);
    int oldValue = read(index);
    newValue = (oldValue & ~mask) | newValue;
    write(index, newValue);
    return (byte)(oldValue >>> shift);
  }

  @SuppressWarnings("cast")
  public synchronized char setChar(Address address, char value) {
    int shift = (address.toInt() & ~MemoryConstants.INT_MASK) << MemoryConstants.LOG_BITS_IN_BYTE;
    assert shift == 0 || shift == 16: "misaligned 2b access at "+address+", shift="+shift;
    int mask = 0x0000FFFF << shift;
    int newValue = (((int)value) << shift) & mask;
    int index = getIndex(address);
    int oldValue = read(index);
    newValue = (oldValue & ~mask) | newValue;
    write(index, newValue);
    return (char)(oldValue >>> shift);
  }

  public synchronized int setInt(Address address, int value) {
    assert ((address.toInt()) % MemoryConstants.BYTES_IN_INT) == 0: "misaligned 4b access at "+address;
    int index = getIndex(address);
    int old = read(index);
    write(index, value);
    return old;
  }

  public synchronized long setLong(Address address, long value) {
    if (SimulatedMemory.ALIGN_CHECK_LONG) {
      assert ((address.toInt()) % MemoryConstants.BYTES_IN_LONG) == 0: "misaligned 8b access at "+address;
    }
    try {
    int index = getIndex(address);
    long old = longFrom2Ints(read(index+1), read(index));
    write(index, (int)(value & 0xFFFFFFFFL));
    write(index+1, (int)(value >>> 32));
    return old;
    } catch (RuntimeException e) {
      System.err.println("Error setting address "+address);
      throw e;
    }
  }

  public synchronized boolean exchangeInt(Address address, int oldValue, int value) {
    int old = getInt(address);
    if (old != oldValue) return false;
    setInt(address,value);
    return true;
  }

  public synchronized boolean exchangeLong(Address address, long oldValue, long value) {
    long old = getLong(address);
    if (old != oldValue) return false;
    setLong(address,value);
    return true;
  }

  /**
   * Perform the actual read of memory.
   */
  private synchronized int read(int index) {
    int value = data[index];
    if (isWatched(index)) {
      Trace.printf("%4d  load %s = %08x%n", Thread.currentThread().getId(),
          cellAddress(index), value);
      //new Throwable().printStackTrace();
    }
    return value;
  }

  /**
   * Perform the actual write of memory, possibly reporting values if watching is enabled for the given address.
   */
  private void write(int index, int value) {
    if (isWatched(index)) {
      Trace.printf("%4d store %s: %08x -> %08x%n", Thread.currentThread().getId(),
          cellAddress(index), data[index], value);
      //new Throwable().printStackTrace();
    }
    data[index] = value;
  }

  /*************************************************************************
   *                      Watch-point management
   */

  /**
   * Return a boolean array indicating which words in this page are being watched.
   */
  private boolean[] getWatchPoints() {
    boolean[] result = null;
    for(Address addr: SimulatedMemory.watches) {
      if (SimulatedMemory.onSamePage(addr,pageAddress)) {
        if (result == null) {
          result = new boolean[data.length];
        }
        int index = getIndex(addr);
        result[index] = true;
        System.err.println("Watching address "+addr);
      }
    }
    return result;
  }

  /**
   * Is the given word being watched ?
   * @param index
   * @return
   */
  private boolean isWatched(int index) {
    return hasWatches() ? watch[index] : false;
  }

  /**
   * @return {@code true} if there are watch-points on this page
   */
  boolean hasWatches() {
    return watch != null;
  }
}
