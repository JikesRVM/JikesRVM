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

import java.util.ArrayList;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Scheduler;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

import static org.vmmagic.unboxed.harness.MemoryConstants.*;

/**
 * Simulated memory
 */
public final class SimulatedMemory {

  static final boolean ALIGN_CHECK_LONG = false;

  static final ArrayList<Address> watches = new ArrayList<Address>();

  static final PageTable pageTable = new PageTable();

  /**
   * Watch mutations to an address (watches 4 byte values)
   * @param watchAddress Address to watch
   */
  public static void addWatch(Address watchAddress) {
    watches.add(watchAddress);
  }

  /**
   * Watch an address range ('bytes' bytes starting from watchAddress).
   * @param watchAddress Address to watch
   * @param bytes Number of bytes to watch
   */
  public static void addWatch(Address watchAddress, int bytes) {
    while (bytes > 0) {
      addWatch(watchAddress);
      bytes -= BYTES_IN_INT;
      watchAddress = watchAddress.plus(BYTES_IN_INT);
    }
  }

  /**
   * Are two addresses on the same page ?
   * @param addr1
   * @param addr2
   * @return
   */
  static boolean onSamePage(Address addr1, Address addr2) {
    return addr2.toWord().xor(addr1.toWord()).LT(Word.fromIntSignExtend(BYTES_IN_PAGE));
  }

  /**
   * Get a page with an int offset
   * @param address
   * @param offset
   * @return
   */
  private static MemoryPage getPage(Address address) {
    Scheduler.yield();
    if (address.isZero()) {
      throw new RuntimeException("Attempted to dereference a null address");
    }
    return pageTable.getPage(address);
  }

  /**
   * @param address Address to load
   * @return The byte at <code>address</code>
   */
  public static byte getByte(Address address) {
    return getPage(address).getByte(address);
  }
  /**
   * @param address Address to load
   * @return The char at <code>address</code>
   */
  public static char getChar(Address address) {
    return getPage(address).getChar(address);
  }
  /**
   * @param address Address to load
   * @return The short at <code>address</code>
   */
  public static short getShort(Address address) {
    return (short)getChar(address);
  }
  /**
   * @param address Address to load
   * @return The int at <code>address</code>
   */
  public static int getInt(Address address) {
    return getPage(address).getInt(address);
  }
  /**
   * @param address Address to load
   * @return The float at <code>address</code>
   */
  public static float getFloat(Address address) {
    return Float.intBitsToFloat(getInt(address));
  }
  /**
   * @param address Address to load
   * @return The long at <code>address</code>
   */
  public static long getLong(Address address) {
    return getPage(address).getLong(address);
  }
  /**
   * @param address Address to load
   * @return The double at <code>address</code>
   */
  public static double getDouble(Address address) {
    return Double.longBitsToDouble(getLong(address));
  }
  /**
   * @param address Address to load
   * @return The ArchitecturalWord at <code>address</code>
   */
  public static ArchitecturalWord getWord(Address address) {
    switch (ArchitecturalWord.getModel()) {
      case BITS32:
        return ArchitecturalWord.fromIntSignExtend(getInt(address));
      case BITS64:
        return ArchitecturalWord.fromLong(getLong(address));
    }
    throw new RuntimeException("ArchitecturalWord.model is neither 32 or 64 bits");
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The byte previously at <code>address</code>
   */
  public static byte setByte(Address address, byte value) {
    return getPage(address).setByte(address, value);
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static char setChar(Address address, char value) {
    return getPage(address).setChar(address, value);
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static short setShort(Address address, short value) {
    return (short)setChar(address, (char)value);
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static int setInt(Address address, int value) {
    return getPage(address).setInt(address, value);
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static float setFloat(Address address, float value) {
    return Float.intBitsToFloat(setInt(address, Float.floatToIntBits(value)));
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static long setLong(Address address, long value) {
    return getPage(address).setLong(address, value);
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static double setDouble(Address address, double value) {
    return Double.longBitsToDouble(setLong(address, Double.doubleToLongBits(value)));
  }

  /**
   * @param address Address to store into
   * @param value The new value
   * @return The previous value of <code>address</code>
   */
  public static ArchitecturalWord setWord(Address address, ArchitecturalWord value) {
    switch (ArchitecturalWord.getModel()) {
      case BITS32:
        return ArchitecturalWord.fromLong(setInt(address, value.toInt()));
      case BITS64:
        return ArchitecturalWord.fromLong(setLong(address, value.toLongSignExtend()));
    }
    throw new RuntimeException("ArchitecturalWord.model is neither 32 or 64 bits");
  }

  /**
   * Atomic compare-and-swap operation
   *
   * @param address Address to swap
   * @param oldValue Expected value of (address)
   * @param value The new value
   * @return Whether the exchange succeeded
   */
  public static boolean exchangeInt(Address address, int oldValue, int value) {
    return getPage(address).exchangeInt(address, oldValue, value);
  }

  /**
   * Atomic compare-and-swap operation
   *
   * @param address Address to swap
   * @param oldValue Expected value of (address)
   * @param value The new value
   * @return Whether the exchange succeeded
   */
  public static boolean exchangeWord(Address address, ArchitecturalWord oldValue, ArchitecturalWord value) {
    switch (ArchitecturalWord.getModel()) {
      case BITS32:
        return getPage(address).exchangeInt(address, oldValue.toInt(), value.toInt());
      case BITS64:
        return getPage(address).exchangeLong(address, oldValue.toLongSignExtend(), value.toLongSignExtend());
    }
    throw new RuntimeException("ArchitecturalWord.model is neither 32 or 64 bits");
  }

  /**
   * Demand zero mmaps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean map(Address start, int size) {
    Object[] args = { start.toString(), size };
    Trace.trace(Item.MEMORY,"map(%s,%d)\n", args);
    Address last = start.plus(size);

    assert start.toWord().and(Word.fromIntSignExtend(~PAGE_MASK)).EQ(Word.zero());

    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      pageTable.mapPage(p);
    }
    return true;
  }

  /**
   * Protects access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean protect(Address start, int size) {
    assert start.toWord().and(Word.fromIntSignExtend(~PAGE_MASK)).EQ(Word.zero());
    Trace.trace(Item.MEMORY,"protect(%s,%d)\n", start.toString(), size);
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      pageTable.setNonReadable(p);
    }
    return true;
  }

  /**
   * Allows access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean unprotect(Address start, int size) {
    assert start.toWord().and(Word.fromIntSignExtend(~PAGE_MASK)).EQ(Word.zero());
    Trace.trace(Item.MEMORY,"unprotect(%s,%d)\n", start.toString(), size);
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      pageTable.setReadable(p);
    }
    return true;
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param size Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, Extent size) {
    Trace.trace(Item.MEMORY,"zero(%s,%s)\n", start.toString(), size.toString());
    zero(start, size.toInt());
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param size Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, int size) {
    Trace.trace(Item.MEMORY,"zero(%s,%d)\n", start.toString(), size);
    assert (size % BYTES_IN_WORD == 0) : "Must zero word rounded bytes";
    MemoryPage page = getPage(start);
    Address pageAddress = start;
    for(int i=0; i < size; i += BYTES_IN_INT) {
      Address curAddr = start.plus(i);
      if (!onSamePage(pageAddress, curAddr)) {
        page = getPage(curAddr);
        pageAddress=curAddr;
      }
      page.setInt(curAddr, 0);
    }
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param size Length in bytes of range (must be multiple of page size)
   */
  public static void zeroPages(Address start, int size) {
    assert start.toWord().and(Word.fromIntSignExtend(~PAGE_MASK)).EQ(Word.zero());
    Trace.trace(Item.MEMORY,"zeroPages(%s,%d)\n", start.toString(), size);
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      pageTable.zeroPage(p);
    }
  }

  /**
   * Logs the contents of an address and the surrounding memory to the
   * error output.
   *
   * @param start the address of the memory to be dumped
   * @param beforeBytes the number of bytes before the address to be
   * included
   * @param afterBytes the number of bytes after the address to be
   * included
   */
  public static void dumpMemory(Address start, int beforeBytes, int afterBytes) {
    Address begin = Address.fromIntZeroExtend((start.toInt() - beforeBytes) & WORD_MASK);
    int bytes = (beforeBytes + afterBytes);
    for(int i=0; i < bytes; i += BYTES_IN_WORD) {
      Address cur = begin.plus(i);
      System.err.println(cur + ": " + getWord(cur));
    }
  }
}
