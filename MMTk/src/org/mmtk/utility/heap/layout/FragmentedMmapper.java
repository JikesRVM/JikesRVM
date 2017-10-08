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
package org.mmtk.utility.heap.layout;

import static org.mmtk.utility.Constants.*;

import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.statistics.EventCounter;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class implements mmapping and protection of virtual memory.<p>
 *
 * This version of Mmapper is like the ByteMapMmapper, except that a number
 * of byte maps are held in a hash table.  The high-order bits of an address
 * are used to hash into the table, and then the low-order bits index into the table.<p>
 *
 * The finest grain unit of memory that is mapped and unmapped is known as a chunk,
 * or MMAP_CHUNK.  The address range addressable by a table of MMAP_CHUNKs
 * is known as an MMAP_SLAB.
 */
@Uninterruptible
public final class FragmentedMmapper extends Mmapper {

  /****************************************************************************
   * Constants
   */

  /**
   * Enable messages at runtime
   */
  private static final boolean VERBOSE = false;

  /**
   * Enable messages in the BootImageWriter log file
   */
  private static final boolean VERBOSE_BUILD = true;

  /**
   * Gather statistics about operation counts (performance impact)
   */
  private static final boolean STATS = false;

  /*
   * Contents of the mapped page array
   */
  /** Page is unmapped */
  private static final byte UNMAPPED = 0;
  /** Page is mapped */
  private static final byte MAPPED = 1;
  /** Page is mapped and marked inaccessible */
  private static final byte PROTECTED = 2;

  /** Maximum mappable address space */
  private static final int LOG_MAPPABLE_BYTES = (LOG_BYTES_IN_ADDRESS_SPACE == 32) ?
      32 :  // can map all virtual space
      36;  // 128GB - physical memory larger than this is uncommon



  /*
   * Size of a slab.  The value 10 gives a slab size of 1GB, with 1024
   * chunks per slab, ie a 1k slab map.  In a 64-bit address space, this
   * will require 1M of slab maps.
   */
  private static final int LOG_MMAP_CHUNKS_PER_SLAB = 10;
  private static final int LOG_MMAP_SLAB_BYTES = LOG_MMAP_CHUNKS_PER_SLAB + VMLayoutConstants.LOG_MMAP_CHUNK_BYTES;
  private static final Extent MMAP_SLAB_EXTENT = Word.fromLong(1L << LOG_MMAP_SLAB_BYTES).toExtent();
  private static final Word MMAP_SLAB_MASK = Word.fromLong((1L << LOG_MMAP_SLAB_BYTES) - 1);

  /**
   * Maximum number of slabs, which determines the maximum mappable address space.
   */
  private static final int LOG_MAX_SLABS =  LOG_MAPPABLE_BYTES - VMLayoutConstants.LOG_MMAP_CHUNK_BYTES - LOG_MMAP_CHUNKS_PER_SLAB;
  private static final int MAX_SLABS =  1 << LOG_MAX_SLABS;

  /**
   * Parameters for the slab table.  The hash function requires it to be
   * a power of 2.  Must be larger than MAX_SLABS for hashing to work,
   * and should be much larger for it to be efficient.
   */
  private static final int LOG_SLAB_TABLE_SIZE = 1 + LOG_MAX_SLABS;

  private static final Word HASH_MASK = Word.fromLong((1L << LOG_SLAB_TABLE_SIZE) - 1);

  private static final int SLAB_TABLE_SIZE = 1 << LOG_SLAB_TABLE_SIZE;

  private static final Address SENTINEL = Word.max().toAddress();

  /**
   * Number of chunks in a slab.
   */
  private static final int MMAP_NUM_CHUNKS = 1 << LOG_MMAP_CHUNKS_PER_SLAB;

  static {
    if (VERBOSE_BUILD) {
      Log.writeln("== FragmentedMmap ==");
      Log.writeln("LOG_MAPPABLE_BYTES       = ", LOG_MAPPABLE_BYTES);
      Log.writeln("LOG_MMAP_CHUNK_BYTES = ", VMLayoutConstants.LOG_MMAP_CHUNK_BYTES);
      Log.writeln("MMAP_CHUNK_BYTES     = ", MMAP_CHUNK_BYTES);
      Log.writeln("LOG_MMAP_SLAB_BYTES  = ", LOG_MMAP_SLAB_BYTES);
      Log.writeln("MMAP_SLAB_EXTENT     = ", MMAP_SLAB_EXTENT);
      Log.writeln("LOG_MMAP_CHUNKS_PER_SLAB = ", LOG_MMAP_CHUNKS_PER_SLAB);
      Log.writeln("LOG_MAX_SLABS = ", LOG_MAX_SLABS);
      Log.writeln("MAX_SLABS     = ", MAX_SLABS);
      Log.writeln("SLAB_TABLE_SIZE = ", SLAB_TABLE_SIZE);
      Log.writeln("MMAP_NUM_CHUNKS = ", MMAP_NUM_CHUNKS);

      Log.write("Total memory used (kB) = ", (MMAP_NUM_CHUNKS * MAX_SLABS) >> 10);
      Log.writeln("==");
    }
  }

  /****************************************************************************
   * Instance variables
   */

  /**
   *
   */
  public final Lock lock = VM.newLock("Mmapper");

  /** Index of the next free slab to be allocated */
  private int freeSlabIndex = 0;

  /** Table of free slab maps,  */
  private final byte[][] freeSlabs;

  /** Table of slabs in use.  Allocated using the slabMap.  */
  private final byte[][] slabTable;

  private final AddressArray slabMap;

  private final EventCounter mapCounter = STATS ? new EventCounter("mmap",true,true) : null;
  private final EventCounter protCounter = STATS ? new EventCounter("prot",true,true) : null;
  private final EventCounter hashAttemptCounter = STATS ? new EventCounter("mmapHit",true,true) : null;
  private final EventCounter hashMissCounter = STATS ? new EventCounter("mmapMiss",true,true) : null;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  public FragmentedMmapper() {
    slabMap = AddressArray.create(SLAB_TABLE_SIZE);
    for (int i = 0; i < SLAB_TABLE_SIZE; i++) {
      slabMap.set(i, SENTINEL);
    }
    slabTable = new byte[SLAB_TABLE_SIZE][];
    freeSlabs = new byte[MAX_SLABS][];
    for (int i = 0; i < MAX_SLABS; i++) {
      freeSlabs[i] = newSlab();
    }
  }

  @Interruptible
  private static byte[] newSlab() {
    byte[] mapped = new byte[MMAP_NUM_CHUNKS];
    for (int c = 0; c < MMAP_NUM_CHUNKS; c++) {
      mapped[c] = UNMAPPED;
    }
    return mapped;
  }

  /**
   * Hash an address down to a slab table index.  First cut: mask off the intra-slab
   * bits, divide the high order bits into LOG_SLAB_TABLE_SIZE pieces and XOR them
   * together.
   * @param addr Address to hash
   * @return A hash code in the range 0..2^LOG_SLAB_TABLE_SIZE
   */
  static int hash(Address addr) {
    Word initial = addr.toWord().and(MMAP_SLAB_MASK.not()).rshl(LOG_MMAP_SLAB_BYTES);
    Word hash = Word.zero();
    while (!initial.isZero()) {
      hash = hash.xor(initial.and(HASH_MASK));
      initial = initial.rshl(LOG_SLAB_TABLE_SIZE);
    }
    return hash.toInt();
  }

  /**
   * Look up the hash table and return the corresponding slab of chunks.
   * @param addr address to locate
   * @return the address of the chunk map for the slab
   */
  byte[] slabTable(Address addr) {
    return slabTable(addr, true);
  }

  /**
   * Look up the hash table and return the corresponding slab of chunks.
   *
   * @param addr Address of to locate
   * @param allocate If {@code true} and there is no corresponding slab in
   *        the hash table, allocate a new one. If {@code false}, return null.
   * @return The address of the chunk map for the slab.
   */
  byte[] slabTable(Address addr, boolean allocate) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!addr.EQ(SENTINEL));
    }
    Address base = addr.toWord().and(MMAP_SLAB_MASK.not()).toAddress();
    final int hash = hash(base);
    int index = hash;  // Use 'index' to iterate over the hash table so that we remember where we started
    if (STATS) hashAttemptCounter.inc();
    while (true) {
      /* Check for a hash-table hit.  Should be the frequent case. */
      if (base.EQ(slabMap.get(index))) {
        return slabTableFor(addr, index);
      }
      if (STATS) hashMissCounter.inc();

      lock.acquire();

      /* Check whether another thread has allocated a slab while we were acquiring the lock */
      if (base.EQ(slabMap.get(index))) {
        lock.release();
        return slabTableFor(addr, index);
      }

      /* Check for a free slot */
      if (slabMap.get(index).EQ(SENTINEL)) {
        if (!allocate) {
          lock.release();
          return null;
        }
        commitFreeSlab(index);
        slabMap.set(index, base);
        lock.release();
        return slabTableFor(addr, index);
      }
      lock.release();
      index = (++index) % SLAB_TABLE_SIZE;
      if (index == hash) {
        VM.assertions.fail("MMAP slab table is full!");
        return null;
      }
    }
  }

  public byte[] slabTableFor(Address addr, int index) {
    byte[] slabTableElement = slabTable[index];
    if (VM.VERIFY_ASSERTIONS) {
      if (slabTableElement == null) {
        Log.write("Addr = "); Log.write(addr);
        Log.write(" slabTable["); Log.write(index);
        Log.writeln("] == null");
      }
      VM.assertions._assert(slabTableElement != null);
    }
    return slabTableElement;
  }

  /**
   * Take a free slab of chunks from the freeSlabs array, and insert it
   * at the correct index in the slabTable.
   * @param index slab table index
   */
  void commitFreeSlab(int index) {
    if (freeSlabIndex >= MAX_SLABS) {
      VM.assertions.fail("All free slabs used: virtual address space is exhausled.");
    }
    VM.barriers.objectArrayStoreNoGCBarrier(slabTable, index, freeSlabs[freeSlabIndex]);
    VM.barriers.objectArrayStoreNoGCBarrier(freeSlabs,freeSlabIndex,null);
    freeSlabIndex++;
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
  @Override
  public void eagerlyMmapAllSpaces(AddressArray spaceMap) {
    // FIXME is this still supported? Currently, neither Mmapper
    // implementation has code for this. We ought to have code for
    // this is in at least one of the implementations or remove
    // the option of eagerly mmapping altogether (including the
    // command line option).

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
  @Override
  public void markAsMapped(Address start, int bytes) {
    final Address end = start.plus(bytes);

    if (VERBOSE) {
      Log.write("Pre-mapping [", start);
      Log.write(":", end);
      Log.writeln("]");
    }

    // Iterate over the slabs covered
    while (start.LT(end)) {
      Address high = end.GT(slabLimit(start)) && !slabLimit(start).isZero() ? slabLimit(start) : end;
      Address slab = slabAlignDown(start);
      int startChunk = chunkIndex(slab, start);
      int endChunk = chunkIndex(slab,chunkAlignUp(high));

      if (VERBOSE) {
        Log.write("  Pre-mapping chunks ", startChunk);
        Log.write(":", endChunk);
        Log.writeln(" in slab ", slabAlignDown(start));
      }
      byte[] mapped = slabTable(start);
      for (int i = startChunk; i < endChunk; i++) {
        if (VERBOSE) {
          Log.write("    Pre-mapping chunk ", slabAlignDown(start));
          Log.write("[");
          Log.write(i);
          Log.writeln("]");
        }
        mapped[i] = MAPPED;
      }
      start = high;
    }
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
  @Override
  public void ensureMapped(Address start, int pages) {
    if (STATS) mapCounter.inc();
    final Address end = start.plus(Conversions.pagesToBytes(pages));
    if (VERBOSE) {
      Log.write("Ensuring [", start);
      Log.write(":", end);
      Log.writeln("]");
    }

    // Iterate over the slabs covered
    while (start.LT(end)) {
      Address base = slabAlignDown(start);
      Address high = end.GT(slabLimit(start)) && !slabLimit(start).isZero() ? slabLimit(start) : end;

      Address slab = slabAlignDown(start);
      int startChunk = chunkIndex(slab, start);
      int endChunk = chunkIndex(slab,chunkAlignUp(high));

      byte[] mapped = slabTable(start);
      if (VERBOSE) {
        Log.write("  Ensuring chunks ", startChunk);
        Log.write(":", endChunk);
        Log.writeln(" in slab ", base);
      }

      /* Iterate over the chunks within the slab */
      for (int chunk = startChunk; chunk < endChunk; chunk++) {
        if (mapped[chunk] == MAPPED) continue;
        Address mmapStart = chunkIndexToAddress(base, chunk);
        lock.acquire();

        // might have become MAPPED here
        if (mapped[chunk] == UNMAPPED) {
          if (VERBOSE) {
            Log.write("    Mapping chunk ", base);
            Log.write("[", chunk);
            Log.write("] (", mmapStart);
            Log.write(":", mmapStart.plus(MMAP_CHUNK_BYTES));
            Log.writeln(")");
          }
          int errno = VM.memory.dzmmap(mmapStart, MMAP_CHUNK_BYTES);
          if (errno != 0) {
            lock.release();
            Log.write("ensureMapped failed with errno ", errno);
            Log.writeln(" on address ", mmapStart);
            VM.assertions.fail("Can't get more space with mmap()");
          } else {
            if (VERBOSE) {
              Log.write("    mmap succeeded at chunk ", chunk);
              Log.write("  ", mmapStart);
              Log.writeln(" with len = ", MMAP_CHUNK_BYTES);
            }
          }
        }
        if (mapped[chunk] == PROTECTED) {
          if (!VM.memory.munprotect(mmapStart, MMAP_CHUNK_BYTES)) {
            lock.release();
            VM.assertions.fail("Mmapper.ensureMapped (unprotect) failed");
          } else {
            if (VERBOSE) {
              Log.write("    munprotect succeeded at chunk ", chunk);
              Log.write("  ", mmapStart);
              Log.writeln(" with len = ", MMAP_CHUNK_BYTES);
            }
          }
        }
        mapped[chunk] = MAPPED;
        lock.release();
      }
      start = high;
    }
  }

  /**
   * Memory protect a range of pages (using mprotect or equivalent).  Note
   * that protection occurs at chunk granularity, not page granularity.
   *
   * @param start The start of the range to be protected.
   * @param pages The size of the range to be protected, in pages
   */
  @Override
  public void protect(Address start, int pages) {
    if (STATS) protCounter.inc();
    final Address end = start.plus(Conversions.pagesToBytes(pages));

    if (VERBOSE) {
      Log.write("Protecting [", start);
      Log.write(":", end);
      Log.writeln("]");
    }
    lock.acquire();
    // Iterate over the slabs covered
    while (start.LT(end)) {
      Address base = slabAlignDown(start);
      Address high = end.GT(slabLimit(start)) && !slabLimit(start).isZero() ? slabLimit(start) : end;

      Address slab = slabAlignDown(start);
      int startChunk = chunkIndex(slab, start);
      int endChunk = chunkIndex(slab,chunkAlignUp(high));

      byte[] mapped = slabTable(start);

      for (int chunk = startChunk; chunk < endChunk; chunk++) {
        if (mapped[chunk] == MAPPED) {
          Address mmapStart = chunkIndexToAddress(base, chunk);
          if (!VM.memory.mprotect(mmapStart, MMAP_CHUNK_BYTES)) {
            lock.release();
            VM.assertions.fail("Mmapper.mprotect failed");
          } else {
            if (VERBOSE) {
              Log.write("    mprotect succeeded at chunk ", chunk);
              Log.write("  ", mmapStart);
              Log.writeln(" with len = ", MMAP_CHUNK_BYTES);
            }
          }
          mapped[chunk] = PROTECTED;
        } else {
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(mapped[chunk] == PROTECTED);
        }
      }
      start = high;
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
  @Override
  @Uninterruptible
  public boolean addressIsMapped(Address addr) {
    byte[] mapped = slabTable(addr, false /* don't allocate */);
    return (mapped != null) && (mapped[chunkIndex(slabAlignDown(addr),addr)] == MAPPED);
  }

  /**
   * Return {@code true} if the given object has been mmapped
   *
   * @param object The object in question.
   * @return {@code true} if the given object has been mmapped
   */
  @Override
  @Uninterruptible
  public boolean objectIsMapped(ObjectReference object) {
    return addressIsMapped(VM.objectModel.refToAddress(object));
  }

  public Address chunkIndexToAddress(Address base, int chunk) {
    return base.plus(chunk << VMLayoutConstants.LOG_MMAP_CHUNK_BYTES);
  }

  /** @return the base address of the enclosing slab */
  Address slabAlignDown(Address addr) {
    return addr.toWord().and(MMAP_SLAB_MASK.not()).toAddress();
  }

  /** @return the base address of the next slab */
  Address slabLimit(Address addr) {
    return slabAlignDown(addr).plus(MMAP_SLAB_EXTENT);
  }

  /**
   * @param slab Address of the slab
   * @param addr Address within a chunk (could be in the next slab)
   * @return The index of the chunk within the slab (could be beyond the end of the slab)
   */
  int chunkIndex(Address slab, Address addr) {
    Offset delta = addr.diff(slab);
    return delta.toWord().rshl(VMLayoutConstants.LOG_MMAP_CHUNK_BYTES).toInt();
  }


}

