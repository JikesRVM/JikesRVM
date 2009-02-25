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
package org.mmtk.utility;

import org.mmtk.utility.heap.*;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 import org.jikesrvm.Offset;
 * Conversions between different units.
 */
@Uninterruptible public class Conversions implements Constants {

  // public static Address roundDownVM(Address addr) {
  //   return roundDown(addr.toWord(), VMResource.LOG_BYTES_IN_REGION).toAddress();
  // }

  // public static Extent roundDownVM(Extent bytes) {
  //   return roundDown(bytes.toWord(), VMResource.LOG_BYTES_IN_REGION).toExtent();
  // }

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

  public static int bytesToMmapChunksUp(Extent bytes) {
    return bytes.plus(Mmapper.MMAP_CHUNK_BYTES - 1).toWord().rshl(Mmapper.LOG_MMAP_CHUNK_BYTES).toInt();
  }

  public static int pagesToMmapChunksUp(int pages) {
    return bytesToMmapChunksUp(pagesToBytes(pages));
  }

  public static int addressToMmapChunksDown(Address addr) {
    Word chunk = addr.toWord().rshl(Mmapper.LOG_MMAP_CHUNK_BYTES);
    return chunk.toInt();
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

  public static int addressToMmapChunksUp(Address addr) {
    Word chunk = addr.plus(Mmapper.MMAP_CHUNK_BYTES - 1).toWord().rshl(Mmapper.LOG_MMAP_CHUNK_BYTES);
    return chunk.toInt();
  }

  public static Extent pagesToBytes(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toExtent();
  }

  public static int pagesToMBytes(int pages) {
    return pages >> (LOG_BYTES_IN_MBYTE - LOG_BYTES_IN_PAGE);
  }

  public static int pagesToKBytes(int pages) {
    return pages << (LOG_BYTES_IN_PAGE - LOG_BYTES_IN_KBYTE);
  }

  /**
    @deprecated : use int bytesToPagesUp(Extent bytes) if possible
   */
  @Deprecated
  public static int bytesToPagesUp(int bytes) {
    return bytesToPagesUp(Extent.fromIntZeroExtend(bytes));
  }

  /**
    @deprecated : use int bytesToPagesUp(Extent bytes) if possible
   */
  @Deprecated
  public static int bytesToPages(int bytes) {
    return bytesToPages(Extent.fromIntZeroExtend(bytes));
  }

  public static int bytesToPagesUp(Extent bytes) {
    return bytes.plus(BYTES_IN_PAGE-1).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }

  public static int bytesToPages(Extent bytes) {
    int pages = bytesToPagesUp(bytes);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(pagesToAddress(pages).toWord().toExtent().EQ(bytes));
    return pages;
  }

  public static int bytesToPages(Offset bytes) {
    if (VM.VERIFY_ASSERTIONS) {
      long val = bytes.toLong();
      VM.assertions._assert(val >= MIN_INT && val <= MAX_INT);
    }
    if (bytes.sGE(Offset.zero()))
      return bytesToPagesUp(Extent.fromIntSignExtend(bytes.toInt()));
    else
      return -bytesToPagesUp(Extent.fromIntSignExtend(-bytes.toInt()));
  }

  public static Address mmapChunksToAddress(int chunk) {
    return Word.fromIntZeroExtend(chunk).lsh(Mmapper.LOG_MMAP_CHUNK_BYTES).toAddress();
  }

  public static Address pageAlign(Address address) {
    return address.toWord().rshl(LOG_BYTES_IN_PAGE).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static int pageAlign(int value) {
    return (value>>LOG_BYTES_IN_PAGE)<<LOG_BYTES_IN_PAGE;
  }

  public static boolean isPageAligned(Address address) {
    return pageAlign(address).EQ(address);
  }

  public static boolean isPageAligned(int value) {
    return pageAlign(value) == value;
  }
}
