/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.vmmagic.unboxed;

import java.util.ArrayList;
import java.util.Hashtable;

public final class SimulatedMemory {
  /** Set to true to print debug information */
  private static final boolean VERBOSE = false;

  /**
   * Print a debug message (provided verbose output is enabled)
   */
  private static void log(String message, Object...args) {
    if (VERBOSE) {
      System.err.printf("Mem: " + message, args);
    }
  }

  public static final int LOG_BYTES_IN_PAGE = 12;
  public static final int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  public static final int LOG_BYTES_IN_WORD = 2;
  public static final int LOG_BYTES_IN_SHORT = 1;
  public static final int LOG_BITS_IN_BYTE = 3;
  public static final int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;
  public static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  public static final int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  public static final int PAGE_SIZE = 1 << LOG_BYTES_IN_PAGE;
  public static final int INDEX_MASK = (PAGE_SIZE - 1);
  public static final int WORD_MASK = ~(BYTES_IN_WORD - 1);

  public static final Address HEAP_START      = new Address(0x10000000);
  public static final Address HEAP_END        = new Address(0xA0000000);

  private static final Hashtable<Integer, MemoryPage> pages = new Hashtable<Integer, MemoryPage>();

  private static final Offset ZERO = Offset.zero();
  private static final ArrayList<Address> watches = new ArrayList<Address>();

  public static void addWatch(Address watchAddress) {
    watches.add(watchAddress);
  }

  public static MemoryPage getPage(Address address, Offset offset) {
    int page = (address.value + offset.value) >>> LOG_BYTES_IN_PAGE;
    return getPage(page);
  }

  public static MemoryPage getPage(int page) {
    MemoryPage p = pages.get(page);
    if (p == null) {
      throw new RuntimeException("Page not mapped: " + Address.formatInt(page << LOG_BYTES_IN_PAGE));
    } else if (!p.readable) {
      throw new RuntimeException("Page not readable: " + Address.formatInt(page << LOG_BYTES_IN_PAGE));
    }
    return p;
  }

  public static byte getByte(Address address) { return getByte(address, ZERO); }
  public static char getChar(Address address) { return getChar(address, ZERO); }
  public static short getShort(Address address) { return getShort(address, ZERO); }
  public static int getInt(Address address) { return getInt(address, ZERO); }
  public static float getFloat(Address address) { return getFloat(address, ZERO); }
  public static long getLong(Address address) { return getLong(address, ZERO); }
  public static double getDouble(Address address) { return getDouble(address, ZERO); }

  public static byte setByte(Address address, byte value) { return setByte(address, value, ZERO); }
  public static char setChar(Address address, char value) { return setChar(address, value, ZERO); }
  public static short setShort(Address address, short value) { return setShort(address, value, ZERO); }
  public static int setInt(Address address, int value) { return setInt(address, value, ZERO); }
  public static float setFloat(Address address, float value) { return setFloat(address, value, ZERO); }
  public static long setLong(Address address, long value) { return setLong(address, value, ZERO); }
  public static double setDouble(Address address, double value) { return setDouble(address, value, ZERO); }

  public static byte getByte(Address address, Offset offset) {
    int byteShift = ((address.value + offset.value) & ~WORD_MASK);
    int bitShift = byteShift << LOG_BITS_IN_BYTE;
    int value = getInt(address.minus(byteShift), offset) >>> bitShift;
    return (byte)value;
  }

  public static char getChar(Address address, Offset offset) {
    int byteShift = ((address.value + offset.value) & ~WORD_MASK);
    int bitShift = byteShift << LOG_BITS_IN_BYTE;
    assert bitShift == 0 || bitShift == 16: "misaligned char access";
    int value = getInt(address.minus(byteShift), offset);
    return (char)(value >>> bitShift);
  }

  public static short getShort(Address address, Offset offset) {
    return (short)getChar(address, offset);
  }

  public static int getInt(Address address, Offset offset) {
    return getPage(address, offset).getInt(address, offset);
  }

  public static float getFloat(Address address, Offset offset) {
    return Float.intBitsToFloat(getInt(address));
  }

  public static long getLong(Address address, Offset offset) {
    return getPage(address, offset).getLong(address, offset);
  }

  public static double getDouble(Address address, Offset offset) {
    return Double.longBitsToDouble(getLong(address));
  }

  public static byte setByte(Address address, byte value, Offset offset) {
    return getPage(address, offset).setByte(address, value, offset);
  }

  public static char setChar(Address address, char value, Offset offset) {
    return getPage(address, offset).setChar(address, value, offset);
  }

  public static short setShort(Address address, short value, Offset offset) {
    return (short)setChar(address, (char)value, offset);
  }

  public static int setInt(Address address, int value, Offset offset) {
    return getPage(address, offset).setInt(address, value, offset);
  }

  public static float setFloat(Address address, float value, Offset offset) {
    return Float.intBitsToFloat(setInt(address, Float.floatToIntBits(value), offset));
  }

  public static long setLong(Address address, long value, Offset offset) {
    return getPage(address, offset).setLong(address, value, offset);
  }

  public static double setDouble(Address address, double value, Offset offset) {
    return Double.longBitsToDouble(setLong(address, Double.doubleToLongBits(value), offset));
  }


  public static boolean exchangeInt(Address address, int oldValue, int value) {
    return exchangeInt(address, oldValue, value, ZERO);
  }

  public static boolean exchangeInt(Address address, int oldValue, int value, Offset offset) {
    return getPage(address, offset).exchangeInt(address, oldValue, value, offset);
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
    log("map(%s,%d)\n", start.toString(), size);
    int first = start.toInt() >>> LOG_BYTES_IN_PAGE;
    int last = (size >>> LOG_BYTES_IN_PAGE) + first;

    for(int p=first; p < last; p++) {
      if (pages.get(p) != null) {
        throw new RuntimeException("Page already mapped: " + Address.formatInt(p << LOG_BYTES_IN_PAGE));
      }
      pages.put(p, new MemoryPage(p));
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
    log("protect(%s,%d)\n", start.toString(), size);
    int first = start.toInt() >> LOG_BYTES_IN_PAGE;
    int last = first + size >> LOG_BYTES_IN_PAGE;
    for(int p=first; p < last; p++) {
      MemoryPage page = pages.get(p);
      if (page == null) {
        throw new RuntimeException("Page not mapped: " + Address.formatInt(p << LOG_BYTES_IN_PAGE));
      }
      page.readable = false;
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
    log("unprotect(%s,%d)\n", start.toString(), size);
    int first = start.toInt() >> LOG_BYTES_IN_PAGE;
    int last = first + size >> LOG_BYTES_IN_PAGE;
    for(int p=first; p < last; p++) {
      MemoryPage page = pages.get(p);
      if (page == null) {
        throw new RuntimeException("Page not mapped: " + Address.formatInt(p << LOG_BYTES_IN_PAGE));
      }
      page.readable = true;
    }
    return true;
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, Extent size) {
    log("zero(%s,%s)\n", start.toString(), size.toString());
    zero(start, size.toInt());
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, int size) {
    log("zero(%s,%d)\n", start.toString(), size);
    assert (size % BYTES_IN_WORD == 0) : "Must zero word rounded bytes";
    for(int i=0; i < size; i += BYTES_IN_WORD) {
      Address cur = start.plus(i);
      getPage(cur, ZERO).setInt(cur, 0, ZERO);
    }
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public static void zeroPages(Address start, int size) {
    log("zeroPages(%s,%d)\n", start.toString(), size);
    int first = start.toInt() >>> LOG_BYTES_IN_PAGE;
    int last = (first + size) >>> LOG_BYTES_IN_PAGE;
    for(int p=first; p < last; p++) {
      MemoryPage page = pages.get(p);
      if (page == null) {
        throw new RuntimeException("Page not mapped: " + Address.formatInt(p << LOG_BYTES_IN_PAGE));
      }
      page.zero();
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
      System.err.println(cur + ": " + Address.formatInt(getInt(cur, ZERO)));
    }
  }

  /**
   * Represents a single page of memory.
   */
  public static final class MemoryPage {
    /** Is this page currently readable */
    private boolean readable;
    /** The page number */
    private int page;
    /** The raw data on this page */
    private int[] data;
    /** Watched indexes */
    private int[] watch;

    /**
     * Create a new MemoryPage to hold the data in a given page.
     */
    private MemoryPage(int page) {
      this.page = page;
      this.readable = true;
      this.data = new int[PAGE_SIZE >>> LOG_BYTES_IN_WORD];
      ArrayList<Integer> watches = new ArrayList<Integer>();
      for(Address addr: SimulatedMemory.watches) {
        if ((addr.value >>> LOG_BYTES_IN_PAGE) == page) {
          watches.add(new Integer(getIndex(addr, ZERO)));
        }
      }
      watch = new int[watches.size()];
      for(int i=0; i < watches.size(); i++) {
        watch[i] = (int)watches.get(i);
      }
    }

    /**
     * Zero the memory in this page.
     */
    public synchronized void zero() {
      for(int i=0; i < data.length; i++) {
        write(i, 0);
      }
    }

    /**
     * Calculate the index to use in the page data based on the given address/offset combination.
     * @param address The address being used.
     * @param offset The offset from this address.
     * @return The index into the page data.
     */
    private int getIndex(Address address, Offset offset) {
      if ((((address.value + offset.value) >>> LOG_BYTES_IN_PAGE) ^ page) != 0) {
        throw new RuntimeException("Invalid access of " + Address.formatInt(address.toInt() + offset.toInt()) + " in page " + Address.formatInt(page << LOG_BYTES_IN_PAGE));
      }
      return ((address.toInt() + offset.toInt()) & INDEX_MASK) >>> LOG_BYTES_IN_WORD;
    }

    /**
     * Load an integer value from this page.
     */
    public int getInt(Address address, Offset offset) {
      assert ((address.value + offset.value) % 4) == 0: "misaligned 4b access";
      return read(getIndex(address, offset));
    }

    /**
     * Load a long value from this page.
     */
    public synchronized long getLong(Address address, Offset offset) {
      assert ((address.value + offset.value) % 8) == 0: "misaligned 8b access";
      return (((long)getInt(address, offset)) << 32) |
             ((long)getInt(address, offset.plus(BYTES_IN_WORD)));
    }

    public synchronized byte setByte(Address address, byte value, Offset offset) {
      int shift = ((address.value + offset.value) & ~WORD_MASK) << LOG_BITS_IN_BYTE;
      int mask = 0x000000FF << shift;
      int newValue = (((int)value) << shift) & mask;
      int index = getIndex(address, offset);
      int oldValue = read(index);
      newValue = (oldValue & ~mask) | newValue;
      write(index, newValue);
      return (byte)(oldValue >>> shift);
    }

    public synchronized char setChar(Address address, char value, Offset offset) {
      int shift = ((address.value + offset.value) & ~WORD_MASK) << LOG_BITS_IN_BYTE;
      assert shift == 0 || shift == 16: "misaligned 2b access";
      int mask = 0x0000FFFF << shift;
      int newValue = (((int)value) << shift) & mask;
      int index = getIndex(address, offset);
      int oldValue = read(index);
      newValue = (oldValue & ~mask) | newValue;
      write(index, newValue);
      return (char)(oldValue >>> shift);
    }

    public synchronized int setInt(Address address, int value, Offset offset) {
      assert ((address.value + offset.value) % 4) == 0: "misaligned 4b access";
      int index = getIndex(address, offset);
      int old = read(index);
      write(index, value);
      return old;
    }

    public synchronized long setLong(Address address, long value, Offset offset) {
      assert ((address.value + offset.value) % 8) == 0: "misaligned 8b access";
      int index = getIndex(address, offset);
      long old = ((long)read(index)) << 32 | ((long)read(index+1));
      write(index, (int)(value >>> 32));
      write(index+1, (int)(value & 0xFFFFFFFFL));
      return old;
    }

    public synchronized boolean exchangeInt(Address address, int oldValue, int value, Offset offset) {
      assert ((address.value + offset.value) % 4) == 0: "misaligned 4b access";
      int index = getIndex(address, offset);
      int old = read(index);
      if (old != oldValue) return false;
      write(index, value);
      return true;
    }

    /**
     * Perform the actual read of memory.
     */
    private int read(int index) {
      int value = data[index];
      return value;
    }

    /**
     * Perform the actual write of memory, possibly reporting values if watching is enabled for the given address.
     */
    private void write(int index, int value) {
      for(int i=0; i < watch.length; i++) {
        if (watch[i] == index) {
          System.err.println(Address.formatInt((page << LOG_BYTES_IN_PAGE) + index) + ": " +
                             Address.formatInt(data[index]) + " -> " +
                             Address.formatInt(value));
        }
      }
      data[index] = value;
    }
  }
}
