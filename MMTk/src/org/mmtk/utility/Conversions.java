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

import static org.mmtk.utility.Constants.*;
import static org.mmtk.utility.heap.layout.VMLayoutConstants.BYTES_IN_CHUNK;

import org.mmtk.utility.heap.layout.VMLayoutConstants;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Conversions between different units.
 */
@Uninterruptible public class Conversions {

  public static Address roundDownMB(Address addr) {
    return roundDown(addr.toWord(), LOG_BYTES_IN_MBYTE).toAddress();
  }

  public static Extent roundDownMB(Extent bytes) {
    return roundDown(bytes.toWord(), LOG_BYTES_IN_MBYTE).toExtent();
  }

  private static Word roundDown(Word value, int logBase) {
    Word mask = Word.one().lsh(logBase).minus(Word.one()).not();
    return value.and(mask);
  }

  public static int roundDown(int value, int alignment) {
    return value & ~(alignment - 1);
  }

  // Round up (if necessary)
  //
  public static int MBToPages(int megs) {
    if (LOG_BYTES_IN_PAGE <= LOG_BYTES_IN_MBYTE)
      return (megs << (LOG_BYTES_IN_MBYTE - LOG_BYTES_IN_PAGE));
    else
      return (megs + ((BYTES_IN_PAGE >>> LOG_BYTES_IN_MBYTE) - 1)) >>> (LOG_BYTES_IN_PAGE - LOG_BYTES_IN_MBYTE);
  }

  public static int addressToPagesDown(Address addr) {
    Word chunk = addr.toWord().rshl(LOG_BYTES_IN_PAGE);
    return chunk.toInt();
  }

  public static int addressToPages(Address addr) {
    int page = addressToPagesDown(addr);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(pagesToAddress(page).EQ(addr));
    return page;
  }

  public static Address pagesToAddress(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static Extent pagesToBytes(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toExtent();
  }

  public static Extent pagesToBytes(long pages) {
    return Word.fromLong(pages).lsh(LOG_BYTES_IN_PAGE).toExtent();
  }

  public static int pagesToMBytes(int pages) {
    return pages >> (LOG_BYTES_IN_MBYTE - LOG_BYTES_IN_PAGE);
  }

  public static int pagesToKBytes(int pages) {
    return pages << (LOG_BYTES_IN_PAGE - LOG_BYTES_IN_KBYTE);
  }

  public static int bytesToPagesUp(Extent bytes) {
    return bytes.plus(BYTES_IN_PAGE - 1).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }

  public static int bytesToPages(Extent bytes) {
    int pages = bytesToPagesUp(bytes);
    if (VM.VERIFY_ASSERTIONS) {
      Extent computedExtent = pagesToAddress(pages).toWord().toExtent();
      boolean bytesMatchPages = computedExtent.EQ(bytes);
      if (!bytesMatchPages) {
        Log.writeln("ERROR: number of bytes computed from pages must match original byte amount!");
        Log.writeln("       bytes = ", bytes);
        Log.writeln("       pages = ", pages);
        Log.writeln("       bytes computed from pages = ", computedExtent);
        VM.assertions._assert(false);
      }
    }
    return pages;
  }

  public static int bytesToPages(Offset bytes) {
    if (VM.VERIFY_ASSERTIONS) {
      long val = bytes.toLong();
      VM.assertions._assert(val >= ((long)MIN_INT) << LOG_BYTES_IN_PAGE && val <= ((long)MAX_INT) << LOG_BYTES_IN_PAGE);
    }
    if (bytes.sGE(Offset.zero()))
      return bytesToPagesUp(bytes.toWord().toExtent());
    else
      return -bytesToPagesUp(Extent.fromLong(-bytes.toLong()));
  }

  public static Address pageAlign(Address address) {
    return address.toWord().rshl(LOG_BYTES_IN_PAGE).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static int pageAlign(int value) {
    return (value >> LOG_BYTES_IN_PAGE) << LOG_BYTES_IN_PAGE;
  }

  public static boolean isPageAligned(Address address) {
    return pageAlign(address).EQ(address);
  }

  public static boolean isPageAligned(int value) {
    return pageAlign(value) == value;
  }

  /**
   * Align an address to a space chunk
   *
   * @param addr The address to be aligned
   * @param down If {@code true} the address will be rounded down, otherwise
   * it will rounded up.
   * @return The chunk-aligned address
   */
  public static Address chunkAlign(Address addr, boolean down) {
    if (!down) addr = addr.plus(BYTES_IN_CHUNK - 1);
    return addr.toWord().rshl(VMLayoutConstants.LOG_BYTES_IN_CHUNK).lsh(VMLayoutConstants.LOG_BYTES_IN_CHUNK).toAddress();
  }

  /**
   * Align an extent to a space chunk
   *
   * @param bytes The extent to be aligned
   * @param down If {@code true} the extent will be rounded down, otherwise
   * it will rounded up.
   * @return The chunk-aligned extent
   */
  public static Extent chunkAlign(Extent bytes, boolean down) {
    return alignWord(bytes.toWord(), VMLayoutConstants.LOG_BYTES_IN_CHUNK, down).toExtent();
  }

  /**
   * Aligns an address to an arbitrary boundary.
   *
   * @param addr The address to be aligned
   * @param bits The log_2 of the boundary size
   * @return The aligned address
   */
  public static Address alignUp(Address addr, int bits) {
    return alignWord(addr.toWord(), bits, false).toAddress();
  }

  /**
   * Align an address to an arbitrary boundary.
   *
   * @param addr The address to be aligned
   * @param bits The log_2 of the boundary size
   * @return The aligned address
   */
  public static Address alignDown(Address addr, int bits) {
    return alignWord(addr.toWord(), bits, true).toAddress();
  }

  @Inline
  public static Word alignWord(Word addr, int bits, boolean down) {
    if (!down) {
      if (BITS_IN_ADDRESS == 64 && bits >= 32) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(bits < 64);
        addr = addr.plus(Word.fromLong((1L << bits) - 1));
      } else {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(bits < 32);
        addr = addr.plus(Word.fromIntZeroExtend((1 << bits) - 1));
      }
    }
    return addr.rshl(bits).lsh(bits);
  }

}
