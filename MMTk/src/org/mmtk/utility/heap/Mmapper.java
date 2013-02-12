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
package org.mmtk.utility.heap;

import org.mmtk.utility.*;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements mmapping and protection of virtual memory.
 */
@Uninterruptible public final class Mmapper implements Constants {

  /****************************************************************************
   * Constants
   */

  /**
   *
   */
  public static final byte UNMAPPED = 0;
  public static final byte MAPPED = 1;
  public static final byte PROTECTED = 2; // mapped but not accessible
  public static final int LOG_MMAP_CHUNK_BYTES = 20;
  public static final int MMAP_CHUNK_BYTES = 1 << LOG_MMAP_CHUNK_BYTES;   // the granularity VMResource operates at
  //TODO: 64-bit: this is not OK: value does not fit in int, but should, we do not want to create such big array
  private static final int MMAP_CHUNK_MASK = MMAP_CHUNK_BYTES - 1;
  private static final int MMAP_NUM_CHUNKS = 1 << (Constants.LOG_BYTES_IN_ADDRESS_SPACE - LOG_MMAP_CHUNK_BYTES);
  public static final boolean verbose = false;

  /****************************************************************************
   * Class variables
   */

  /**
   *
   */
  public static final Lock lock = VM.newLock("Mmapper");
  private static byte[] mapped;


  /****************************************************************************
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    mapped = new byte[MMAP_NUM_CHUNKS];
    for (int c = 0; c < MMAP_NUM_CHUNKS; c++) {
      mapped[c] = UNMAPPED;
    }
  }

  /****************************************************************************
   * Generic mmap and protection functionality
   */

  /**
   * Given an address array describing the regions of virtual memory to be used
   * by MMTk, demand zero map all of them if they are not already mapped.
   *
   * @param spaceMap An address array containing a pairs of start and end
   * addresses for each of the regions to be mappe3d
   */
  public static void eagerlyMmapAllSpaces(AddressArray spaceMap) {

    /*for (int i = 0; i < spaceMap.length() / 2; i++) {
      Address regionStart = spaceMap.get(i * 2);
      Address regionEnd = spaceMap.get(i * 2 + 1);
      Log.write(regionStart); Log.write(" "); Log.writeln(regionEnd);
      if (regionEnd.EQ(Address.zero()) || regionStart.EQ(Address.fromIntSignExtend(-1)) ||regionEnd.EQ(Address.fromIntSignExtend(-1)))
          break;
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(regionStart.EQ(chunkAlignDown(regionStart)));
        VM.assertions._assert(regionEnd.EQ(chunkAlignDown(regionEnd)));
      }
      int pages = Conversions.bytesToPages(regionEnd.diff(regionStart));
      ensureMapped(regionStart, pages);
    }*/
  }

  /**
   *  Mark a range of pages as having (already) been mapped.  This is useful
   *  where the VM has performed the mapping of the pages itself.
   *
   * @param start The start of the range to be marked as mapped
   * @param bytes The size of the range, in bytes.
   */
  public static void markAsMapped(Address start, int bytes) {
    int startChunk = Conversions.addressToMmapChunksDown(start);
    int endChunk = Conversions.addressToMmapChunksUp(start.plus(bytes));
    for (int i = startChunk; i <= endChunk; i++)
      mapped[i] = MAPPED;
  }

  /**
   * Ensure that a range of pages is mmapped (or equivalent).  If the
   * pages are not yet mapped, demand-zero map them. Note that mapping
   * occurs at chunk granularity, not page granularity.<p>
   *
   * NOTE: There is a monotonicity assumption so that only updates require lock
   * acquisition.
   * TODO: Fix the above to support unmapping.
   *
   * @param start The start of the range to be mapped.
   * @param pages The size of the range to be mapped, in pages
   */
  public static void ensureMapped(Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start);
    int endChunk = Conversions.addressToMmapChunksUp(start.plus(Conversions.pagesToBytes(pages)));
    for (int chunk = startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) continue;
      Address mmapStart = Conversions.mmapChunksToAddress(chunk);
      lock.acquire();
//      Log.writeln(mmapStart);
      // might have become MAPPED here
      if (mapped[chunk] == UNMAPPED) {
        int errno = VM.memory.dzmmap(mmapStart, MMAP_CHUNK_BYTES);
        if (errno != 0) {
          lock.release();
          Log.write("ensureMapped failed with errno "); Log.write(errno);
          Log.write(" on address "); Log.writeln(mmapStart);
          VM.assertions.fail("Can't get more space with mmap()");
        } else {
          if (verbose) {
            Log.write("mmap succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_BYTES);
          }
        }
      }
      if (mapped[chunk] == PROTECTED) {
        if (!VM.memory.munprotect(mmapStart, MMAP_CHUNK_BYTES)) {
          lock.release();
          VM.assertions.fail("Mmapper.ensureMapped (unprotect) failed");
        } else {
          if (verbose) {
            Log.write("munprotect succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_BYTES);
          }
        }
      }
      mapped[chunk] = MAPPED;
      lock.release();
    }

  }

  /**
   * Memory protect a range of pages (using mprotect or equivalent).  Note
   * that protection occurs at chunk granularity, not page granularity.
   *
   * @param start The start of the range to be protected.
   * @param pages The size of the range to be protected, in pages
   */
  public static void protect(Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start);
    int chunks = Conversions.pagesToMmapChunksUp(pages);
    int endChunk = startChunk + chunks;
    lock.acquire();
    for (int chunk = startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) {
        Address mmapStart = Conversions.mmapChunksToAddress(chunk);
        if (!VM.memory.mprotect(mmapStart, MMAP_CHUNK_BYTES)) {
          lock.release();
          VM.assertions.fail("Mmapper.mprotect failed");
        } else {
          if (verbose) {
            Log.write("mprotect succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_BYTES);
          }
        }
        mapped[chunk] = PROTECTED;
      } else {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mapped[chunk] == PROTECTED);
      }
    }
    lock.release();
  }

  /****************************************************************************
   * Utility functions
   */

  /**
   * Return {@code true} if the given address has been mmapped
   *
   * @param addr The address in question.
   * @return {@code true} if the given address has been mmapped
   */
  @Uninterruptible
  public static boolean addressIsMapped(Address addr) {
    int chunk = Conversions.addressToMmapChunksDown(addr);
    return mapped[chunk] == MAPPED;
  }

  /**
   * Return {@code true} if the given object has been mmapped
   *
   * @param object The object in question.
   * @return {@code true} if the given object has been mmapped
   */
  @Uninterruptible
  public static boolean objectIsMapped(ObjectReference object) {
    return addressIsMapped(VM.objectModel.refToAddress(object));
  }

  /**
   * Return a given address rounded up to an mmap chunk size
   *
   * @param addr The address to be aligned
   * @return The given address rounded up to an mmap chunk size
   */
  @SuppressWarnings("unused")  // but might be useful someday
  private static Address chunkAlignUp(Address addr) {
    return chunkAlignDown(addr.plus(MMAP_CHUNK_MASK));
  }

  /**
   * Return a given address rounded down to an mmap chunk size
   *
   * @param addr The address to be aligned
   * @return The given address rounded down to an mmap chunk size
   */
  private static Address chunkAlignDown(Address addr) {
    return addr.toWord().and(Word.fromIntSignExtend(MMAP_CHUNK_MASK).not()).toAddress();
  }
}

