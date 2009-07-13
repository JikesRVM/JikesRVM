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
package org.vmmagic.unboxed;

import java.util.ArrayList;
import java.util.HashMap;

import org.mmtk.harness.scheduler.Scheduler;

/**
 * Simulated memory
 */
public final class SimulatedMemory {
  /** Set to true to print debug information */
  private static final boolean VERBOSE = false;

  private static final boolean ALIGN_CHECK_LONG = false;

  /**
   * Print a debug message (provided verbose output is enabled)
   */
  private static void log(String message, Object...args) {
    if (VERBOSE) {
      System.err.printf("Mem: " + message, args);
    }
  }

  /** Log_2 of page size */
  public static final int LOG_BYTES_IN_PAGE = 12;
  /** Log_2 of size(long) */
  public static final int LOG_BYTES_IN_LONG = 3;
  /** Log_2 of size(address) */
  public static final int LOG_BYTES_IN_WORD = ArchitecturalWord.getModel().logBytesInWord();
  /** Log_2 of size(int) */
  public static final int LOG_BYTES_IN_INT = 2;
  /** Log_2 of size(short) */
  public static final int LOG_BYTES_IN_SHORT = 1;
  /** Log_2 of size(byte) */
  public static final int LOG_BITS_IN_BYTE = 3;
  /** size in bytes of a memory page */
  public static final int BYTES_IN_PAGE = 1 << LOG_BYTES_IN_PAGE;
  /** size(long) */
  public static final int BYTES_IN_LONG = 1 << LOG_BYTES_IN_LONG;
  /** size(address), size(word) etc */
  public static final int BYTES_IN_WORD = 1 << LOG_BYTES_IN_WORD;
  /** size(int) */
  public static final int BYTES_IN_INT = 1 << LOG_BYTES_IN_INT;
  /** size(short) */
  public static final int BYTES_IN_SHORT = 1 << LOG_BYTES_IN_SHORT;
  /** size(byte) */
  public static final int BITS_IN_BYTE = 1 << LOG_BITS_IN_BYTE;
  /** Mask of index into page */
  private static final int INDEX_MASK = (BYTES_IN_PAGE - 1);
  /** Mask an offset within an int */
  public static final int INT_MASK = ~(BYTES_IN_INT - 1);
  /** Mask an offset within a word */
  public static final int WORD_MASK = ~(BYTES_IN_WORD - 1);

  /* Dimensions of memory cells (the contents of a memory page) */
  private static final int CELL_MASK = INT_MASK;
  private static final int BYTES_IN_CELL = BYTES_IN_INT;
  private static final int LOG_BYTES_IN_CELL = LOG_BYTES_IN_INT;

  private static final ArrayList<Address> watches = new ArrayList<Address>();

  private static final class PageTable {
    private static final HashMap<Long, MemoryPage> pages = new HashMap<Long, MemoryPage>();

    private static long pageTableEntry(Address p) {
      return p.toLong() >>> LOG_BYTES_IN_PAGE;
    }

    /**
     * Internal: get a page by page number, performing appropriate
     * checking and synchronization
     * @param page
     * @return
     */
    static MemoryPage getPage(Address p) {
      synchronized(pages) {
        MemoryPage page = pages.get(pageTableEntry(p));
        if (page == null) {
          throw new RuntimeException("Page not mapped: " + p);
        } else if (!page.readable) {
          throw new RuntimeException("Page not readable: " + p);
        }
        return page;
      }
    }

    static void setReadable(Address p) {
      synchronized(pages) {
        MemoryPage page = pages.get(pageTableEntry(p));
        if (page == null) {
          throw new RuntimeException("Page not mapped: " + p);
        }
        page.readable = true;
      }
    }

    static void setNonReadable(Address p) {
      synchronized(pages) {
        MemoryPage page = pages.get(pageTableEntry(p));
        if (page == null) {
          throw new RuntimeException("Page not mapped: " + p);
        }
        page.readable = false;
      }
    }

    private static void mapPage(Address p) {
      synchronized(pages) {
        long page = pageTableEntry(p);
        if (VERBOSE) { System.err.printf("Mapping page %s%n", p); }
        if (pages.get(page) != null) {
          throw new RuntimeException("Page already mapped: " + p);
        }
        pages.put(page, new MemoryPage(p));
      }
    }

    private static void zeroPage(Address p) {
      synchronized(pages) {
        MemoryPage page = pages.get(pageTableEntry(p));
        if (page == null) {
          throw new RuntimeException("Page not mapped: " + p);
        }
        page.zero();
      }
    }
  }

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
  private static boolean onSamePage(Address addr1, Address addr2) {
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
    return PageTable.getPage(address);
  }

  static byte getByte(Address address) {
    return getPage(address).getByte(address);
  }
  static char getChar(Address address) {
    return getPage(address).getChar(address);
  }
  static short getShort(Address address) {
    return (short)getPage(address).getChar(address);
  }
  static int getInt(Address address) {
    return getPage(address).getInt(address);
  }
  static float getFloat(Address address) {
    return Float.intBitsToFloat(getInt(address));
  }
  static long getLong(Address address) {
    return getPage(address).getLong(address);
  }
  static double getDouble(Address address) {
    return Double.longBitsToDouble(getLong(address));
  }
  static ArchitecturalWord getWord(Address address) {
    switch (ArchitecturalWord.getModel()) {
      case BITS32:
        return ArchitecturalWord.fromIntSignExtend(getInt(address));
      case BITS64:
        return ArchitecturalWord.fromLong(getPage(address).getLong(address));
    }
    throw new RuntimeException("ArchitecturalWord.model is neither 32 or 64 bits");
  }

  static byte setByte(Address address, byte value) {
    return getPage(address).setByte(address, value);
  }

  static char setChar(Address address, char value) {
    return getPage(address).setChar(address, value);
  }

  static short setShort(Address address, short value) {
    return (short)setChar(address, (char)value);
  }

  static int setInt(Address address, int value) {
    return getPage(address).setInt(address, value);
  }

  static float setFloat(Address address, float value) {
    return Float.intBitsToFloat(setInt(address, Float.floatToIntBits(value)));
  }

  static long setLong(Address address, long value) {
    return getPage(address).setLong(address, value);
  }

  static double setDouble(Address address, double value) {
    return Double.longBitsToDouble(setLong(address, Double.doubleToLongBits(value)));
  }

  static ArchitecturalWord setWord(Address address, ArchitecturalWord value) {
    switch (ArchitecturalWord.getModel()) {
      case BITS32:
        return ArchitecturalWord.fromLong(setInt(address, value.toInt()));
      case BITS64:
        return ArchitecturalWord.fromLong(setLong(address, value.toLongSignExtend()));
    }
    throw new RuntimeException("ArchitecturalWord.model is neither 32 or 64 bits");
  }

  static boolean exchangeInt(Address address, int oldValue, int value) {
    return getPage(address).exchangeInt(address, oldValue, value);
  }

  static boolean exchangeWord(Address address, ArchitecturalWord oldValue, ArchitecturalWord value) {
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
    log("map(%s,%d)\n", start.toString(), size);
    Address last = start.plus(size);

    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      PageTable.mapPage(p);
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
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      PageTable.setNonReadable(p);
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
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      PageTable.setReadable(p);
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
    log("zero(%s,%s)\n", start.toString(), size.toString());
    zero(start, size.toInt());
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param size Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(Address start, int size) {
    log("zero(%s,%d)\n", start.toString(), size);
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
    log("zeroPages(%s,%d)\n", start.toString(), size);
    Address last = start.plus(size);
    for(Address p=start; p.LT(last); p = p.plus(BYTES_IN_PAGE)) {
      PageTable.zeroPage(p);
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

  /**
   * Represents a single page of memory.
   */
  private static final class MemoryPage {
    /** Is this page currently readable */
    private boolean readable;
    /** The raw data on this page */
    private final Address pageAddress;
    private final int[] data;
    /** Watched indexes */
    private final boolean[] watch;

    /**
     * Create a new MemoryPage to hold the data in a given page.
     */
    private MemoryPage(Address pageAddress) {
      this.pageAddress = pageAddress;
      this.readable = true;
      this.data = new int[BYTES_IN_PAGE >>> LOG_BYTES_IN_CELL];
      this.watch = getWatchPoints();
      if (VERBOSE) log("Mapping page %s%n",pageAddress);
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
     * @param high TODO
     * @param low TODO
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
      assert onSamePage(address,pageAddress) :
        "Invalid access of " + address + " in page " + pageAddress;
      return ((int)((address.toLong()) & INDEX_MASK)) >>> LOG_BYTES_IN_CELL;
    }

    /**
     * Load a byte value from this page.
     * @param address Address of byte to return
     * @return The contents of the byte at the address
     */
    public byte getByte(Address address) {
      int bitShift = ((address.toInt()) & ~CELL_MASK) << LOG_BITS_IN_BYTE;
      int index = getIndex(address);
      return (byte)(read(index) >>> bitShift);
    }

    /**
     * Load a char value from this page.
     * @param address Address of char to return
     * @return The contents of the char at the address
     */
    public char getChar(Address address) {
      int bitShift = ((address.toInt()) & ~CELL_MASK) << LOG_BITS_IN_BYTE;
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
      assert ((address.toInt()) % BYTES_IN_INT) == 0: "misaligned 4b access at "+address;
      return read(getIndex(address));
    }

    /**
     * Load a long value from this page.
     * @param address Address of long to return
     * @return The contents of the long at the address
     */
    public long getLong(Address address) {
      if (ALIGN_CHECK_LONG) {
        assert ((address.toLong()) % BYTES_IN_LONG) == 0: "misaligned 8b access at "+address;
      }
      return longFrom2Ints(getInt(address), getInt(address.plus(BYTES_IN_CELL)));
    }

    @SuppressWarnings("cast")
    public byte setByte(Address address, byte value) {
      int shift = ((address.toInt()) & ~WORD_MASK) << LOG_BITS_IN_BYTE;
      int mask = 0x000000FF << shift;
      int newValue = (((int)value) << shift) & mask;
      int index = getIndex(address);
      int oldValue = read(index);
      newValue = (oldValue & ~mask) | newValue;
      write(index, newValue);
      return (byte)(oldValue >>> shift);
    }

    @SuppressWarnings("cast")
    public char setChar(Address address, char value) {
      int shift = ((address.toInt()) & ~WORD_MASK) << LOG_BITS_IN_BYTE;
      assert shift == 0 || shift == 16: "misaligned 2b access at "+address;
      int mask = 0x0000FFFF << shift;
      int newValue = (((int)value) << shift) & mask;
      int index = getIndex(address);
      int oldValue = read(index);
      newValue = (oldValue & ~mask) | newValue;
      write(index, newValue);
      return (char)(oldValue >>> shift);
    }

    public int setInt(Address address, int value) {
      assert ((address.toInt()) % BYTES_IN_INT) == 0: "misaligned 4b access at "+address;
      int index = getIndex(address);
      int old = read(index);
      write(index, value);
      return old;
    }

    public long setLong(Address address, long value) {
      if (ALIGN_CHECK_LONG) {
        assert ((address.toInt()) % BYTES_IN_LONG) == 0: "misaligned 8b access at "+address;
      }
      try {
      int index = getIndex(address);
      long old = longFrom2Ints(read(index), read(index+1));
      write(index, (int)(value >>> 32));
      write(index+1, (int)(value & 0xFFFFFFFFL));
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
    private int read(int index) {
      int value = data[index];
      if (isWatched(index)) {
        System.err.printf("%s = %08x%n", cellAddress(index), data[index]);
        //new Throwable().printStackTrace();
      }
      return value;
    }

    /**
     * Perform the actual write of memory, possibly reporting values if watching is enabled for the given address.
     */
    private void write(int index, int value) {
      if (isWatched(index)) {
        System.err.printf("%s: %08x -> %08x%n", cellAddress(index), data[index], value);
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
        if (onSamePage(addr,pageAddress)) {
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
      return watch == null ? false : watch[index];
    }
  }
}
