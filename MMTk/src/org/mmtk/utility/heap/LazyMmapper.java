/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package org.mmtk.utility.heap;

import org.mmtk.utility.*;
import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Memory;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements lazy mmapping of virtual memory.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class LazyMmapper implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public static methods 
   *
   */

  public static boolean verbose = false;
  public static Lock lock = new Lock("LazyMapper");

  // There is a monotonicity assumption so that only updates require lock acquisition.
  //
  public static void ensureMapped(Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start);
    int endChunk = Conversions.addressToMmapChunksUp(start.add(Conversions.pagesToBytes(pages)));
    for (int chunk=startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) continue;
      Address mmapStart = Conversions.mmapChunksToAddress(chunk);
      lock.acquire();
      // might have become MAPPED here
      lock.check(100);
      if (mapped[chunk] == UNMAPPED) {
        lock.check(101);
        int errno = Memory.mmap(mmapStart, MMAP_CHUNK_SIZE);
        lock.check(102);
        if (errno != 0) {
          lock.release();
          Log.write("ensureMapped failed with errno "); Log.write(errno);
          Log.write(" on address "); Log.writeln(mmapStart);
          if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
        }
        else {
          if (verbose) {
            Log.write("mmap succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_SIZE);
          }
        }
        lock.check(103);
      }
      if (mapped[chunk] == PROTECTED) {
        lock.check(201);
        if (!Memory.munprotect(mmapStart, MMAP_CHUNK_SIZE)) {
          lock.check(202);
          lock.release();
          Assert.fail("LazyMmapper.ensureMapped (unprotect) failed");
        }
        else {
          if (verbose) {
            Log.write("munprotect succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_SIZE);
          }
        }
      }
      lock.check(301);
      mapped[chunk] = MAPPED;
      lock.check(302);
      lock.release();
    }

  }

  public static void protect(Address start, int pages) {
    int startChunk = Conversions.addressToMmapChunksDown(start); 
    int chunks = Conversions.pagesToMmapChunksUp(pages);
    int endChunk = startChunk + chunks;
    lock.acquire();
    for (int chunk=startChunk; chunk < endChunk; chunk++) {
      if (mapped[chunk] == MAPPED) {
        Address mmapStart = Conversions.mmapChunksToAddress(chunk);
        if (!Memory.mprotect(mmapStart, MMAP_CHUNK_SIZE)) {
          lock.release();
          Assert.fail("LazyMmapper.mprotect failed");
        }
        else {
          if (verbose) {
            Log.write("mprotect succeeded at chunk "); Log.write(chunk);  Log.write("  "); Log.write(mmapStart);
            Log.write(" with len = "); Log.writeln(MMAP_CHUNK_SIZE);
          }
        }
        mapped[chunk] = PROTECTED;
      }
      else {
        if (Assert.VERIFY_ASSERTIONS) Assert._assert(mapped[chunk] == PROTECTED);
      }
    }
    lock.release();
  }

  public static boolean addressIsMapped(Address addr) 
    throws UninterruptiblePragma {
    int chunk = Conversions.addressToMmapChunksDown(addr);
    return mapped[chunk] == MAPPED;
  }

  public static boolean objectIsMapped(ObjectReference object) 
    throws UninterruptiblePragma {
    return addressIsMapped(ObjectModel.refToAddress(object));
  }

  /****************************************************************************
   *
   * Private static methods and variables
   */
  final public static byte UNMAPPED = 0;
  final public static byte MAPPED = 1;
  final public static byte PROTECTED = 2;   // mapped but not accessible
  private static byte mapped[];
  final public static int LOG_MMAP_CHUNK_SIZE = 20;            
  final public static int MMAP_CHUNK_SIZE = 1 << LOG_MMAP_CHUNK_SIZE;   // the granularity VMResource operates at
  //TODO: 64-bit: this is not OK: value does not fit in int, but should, we do not want to create such big array
  final private static int MMAP_NUM_CHUNKS = 1 << (Constants.LOG_BYTES_IN_ADDRESS_SPACE - LOG_MMAP_CHUNK_SIZE);

  private static String chunkStateToString(byte state) {
    switch (state) {
    case UNMAPPED: return "UNMAPPED";
    case MAPPED: return "MAPPED";
    case PROTECTED: return "PROTECTED";
    }
    return "UNKNOWN";
  }

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

  public static void boot (Address bootStart, Extent bootSize) {
    int startChunk = Conversions.addressToMmapChunksDown(bootStart);
    int endChunk = Conversions.addressToMmapChunksDown(bootStart.add(bootSize));
    for (int i=startChunk; i<=endChunk; i++)
      mapped[i] = MAPPED;
  }

}

