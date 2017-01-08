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

import static org.mmtk.utility.Constants.LOG_BYTES_IN_PAGE;

import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

/**
 * Careful not to introduce too many dependencies, because static initialization races are
 * prone here.
 */
@Uninterruptible
public class VMLayoutConstants {

  /**
   * Enable messages in the BootImageWriter log file
   */
  static final boolean VERBOSE_BUILD = true;

  /** log_2 of the addressable virtual space */
  public static final int LOG_ADDRESS_SPACE = VM.HEAP_LAYOUT_32BIT ? 32 : HeapParameters.LOG_SPACE_SIZE_64 + HeapParameters.LOG_MAX_SPACES;

  /**
   * log_2 of the coarsest unit of address space allocation.
   * <p>
   * In the 32-bit VM layout, this determines the granularity of
   * allocation in a discontigouous space.  In the 64-bit layout,
   * this determines the growth factor of the large contiguous spaces
   * that we provide.
   */
  public static final int LOG_BYTES_IN_CHUNK = 22;

  /** Coarsest unit of address space allocation. */
  public static final int BYTES_IN_CHUNK = 1 << LOG_BYTES_IN_CHUNK;

  /** Coarsest unit of address space allocation, in pages */
  public static final int PAGES_IN_CHUNK = 1 << (LOG_BYTES_IN_CHUNK - LOG_BYTES_IN_PAGE);

  /** log_2 of the maximum number of chunks we need to track.  Only used in 32-bit layout.*/
  public static final int LOG_MAX_CHUNKS = LOG_ADDRESS_SPACE - LOG_BYTES_IN_CHUNK;

  /** Maximum number of chunks we need to track.  Only used in 32-bit layout. */
  public static final int MAX_CHUNKS = 1 << LOG_MAX_CHUNKS;

  /**
   * An upper bound on the extent of any space in the
   * current memory layout
   */
  public static final int LOG_SPACE_EXTENT = VM.HEAP_LAYOUT_64BIT ? HeapParameters.LOG_SPACE_SIZE_64 : 31;

  /**
   * An upper bound on the extent of any space in the
   * current memory layout
   */
  public static final Extent MAX_SPACE_EXTENT = Extent.fromLong(1L << LOG_SPACE_EXTENT);

  /** Lowest virtual address used by the virtual machine */
  public static final Address HEAP_START = Conversions.chunkAlign(VM.HEAP_START, true);

  /** Highest virtual address used by the virtual machine */
  public static final Address HEAP_END = Conversions.chunkAlign(VM.HEAP_END, false);

  /**
   * Lowest virtual address available for MMTk to manage.  The address space between
   * HEAP_START and AVAILABLE_START comprises memory directly managed by the VM,
   * and not available to MMTk.
   */
  public static final Address AVAILABLE_START = Conversions.chunkAlign(VM.AVAILABLE_START, false);

  /**
   * Highest virtual address available for MMTk to manage.  The address space between
   * HEAP_END and AVAILABLE_END comprises memory directly managed by the VM,
   * and not available to MMTk.
 */
  public static final Address AVAILABLE_END = Conversions.chunkAlign(VM.AVAILABLE_END, true);

  /** Size of the address space available to the MMTk heap. */
  public static final Extent AVAILABLE_BYTES = AVAILABLE_END.diff(AVAILABLE_START).toWord().toExtent();

  /** Granularity at which we map and unmap virtual address space in the heap */
  public static final int LOG_MMAP_CHUNK_BYTES = 20;

  /** log_2 of the number of pages in a 64-bit space */
  public static final int LOG_PAGES_IN_SPACE64 = HeapParameters.LOG_SPACE_SIZE_64 - LOG_BYTES_IN_PAGE;

  /** The number of pages in a 64-bit space */
  public static final int PAGES_IN_SPACE64 = 1 << LOG_PAGES_IN_SPACE64;

  /*
   *  The 64-bit VM layout divides address space into LOG_MAX_SPACES (k) fixed size
   *  regions of size 2^n, aligned at 2^n byte boundaries.  A virtual address can be
   *  subdivided into fields as follows
   *
   *    64                              0
   *    00...0SSSSSaaaaaaaaaaa...aaaaaaaa
   *
   * The field 'S' identifies the space to which the address points.
   */

  /**
   * Number of bits to shift a space index into/out of a virtual address.
   */
   /* In a 32-bit model, use a dummy value so that the compiler doesn't barf. */
  public static final int SPACE_SHIFT_64 = VM.HEAP_LAYOUT_64BIT ? HeapParameters.LOG_SPACE_SIZE_64 : 0;

  /**
   * Bitwise mask to isolate a space index in a virtual address.
   *
   * We can't express this constant in a 32-bit environment, hence the
   * conditional definition.
   */
  public static final Word SPACE_MASK_64 = VM.HEAP_LAYOUT_64BIT ?
      Word.fromLong((1L << HeapParameters.LOG_MAX_SPACES) - 1).lsh(SPACE_SHIFT_64) :
        Word.zero();

  /**
   * Size of each space in the 64-bit memory layout
   *
   * We can't express this constant in a 32-bit environment, hence the
   * conditional definition.
   */
  public static final Extent SPACE_SIZE_64 = VM.HEAP_LAYOUT_64BIT ?
      Word.fromLong(1L << HeapParameters.LOG_SPACE_SIZE_64).toExtent() :
        MAX_SPACE_EXTENT;

  static {
    if (VERBOSE_BUILD) {
      Log.writeln("== VMLayoutConstants ==");
      Log.writeln("LOG_ADDRESS_SPACE    = ", LOG_ADDRESS_SPACE);
      Log.writeln("LOG_BYTES_IN_CHUNK   = ", LOG_BYTES_IN_CHUNK);
      Log.writeln("BYTES_IN_CHUNK       = ", BYTES_IN_CHUNK);
      Log.writeln("LOG_MAX_CHUNKS       = ", LOG_MAX_CHUNKS);
      Log.writeln("MAX_CHUNKS           = ", MAX_CHUNKS);
      Log.writeln("HEAP_START      = ", HEAP_START);
      Log.writeln("HEAP_END        = ", HEAP_END);
      Log.writeln("AVAILABLE_START = ", AVAILABLE_START);
      Log.writeln("AVAILABLE_END   = ", AVAILABLE_END);
      Log.writeln("AVAILABLE_BYTES      = ", AVAILABLE_BYTES);
      Log.writeln("MAX_SPACE_EXTENT     = ", MAX_SPACE_EXTENT);
      Log.writeln("LOG_MMAP_CHUNK_BYTES = ", LOG_MMAP_CHUNK_BYTES);
      Log.writeln("==");
    }
  }
}

