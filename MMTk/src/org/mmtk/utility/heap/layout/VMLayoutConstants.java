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
  public static final int LOG_ADDRESS_SPACE = 32;

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
  public static final int LOG_SPACE_EXTENT = 31;

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

  static {
    if (VERBOSE_BUILD) {
      Log.writeln("== VMLayoutConstants ==");
      Log.write("LOG_ADDRESS_SPACE    = "); Log.writeln(LOG_ADDRESS_SPACE);
      Log.write("LOG_BYTES_IN_CHUNK   = "); Log.writeln(LOG_BYTES_IN_CHUNK);
      Log.write("BYTES_IN_CHUNK       = "); Log.writeln(BYTES_IN_CHUNK);
      Log.write("LOG_MAX_CHUNKS       = "); Log.writeln(LOG_MAX_CHUNKS);
      Log.write("MAX_CHUNKS           = "); Log.writeln(MAX_CHUNKS);
      Log.write("HEAP_START      = "); Log.writeln(HEAP_START);
      Log.write("HEAP_END        = "); Log.writeln(HEAP_END);
      Log.write("AVAILABLE_START = "); Log.writeln(AVAILABLE_START);
      Log.write("AVAILABLE_END   = "); Log.writeln(AVAILABLE_END);
      Log.write("AVAILABLE_BYTES      = "); Log.writeln(AVAILABLE_BYTES);
      Log.write("MAX_SPACE_EXTENT     = "); Log.writeln(MAX_SPACE_EXTENT);
      Log.write("LOG_MMAP_CHUNK_BYTES = "); Log.writeln(LOG_MMAP_CHUNK_BYTES);
      Log.writeln("==");
    }
  }
}

