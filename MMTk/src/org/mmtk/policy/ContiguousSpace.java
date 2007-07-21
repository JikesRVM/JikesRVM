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
package org.mmtk.policy;

import org.mmtk.utility.Log;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.utility.heap.SpaceDescriptor;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

/**
 * The continuous space requires that the managed virtual memory is contiguous.
 */
@Uninterruptible
public abstract class ContiguousSpace extends Space {

  /****************************************************************************
   *
   * Class variables
   */
  private static boolean DEBUG = false;

  /****************************************************************************
   *
   * Instance variables
   */
  protected Address start;
  protected Extent extent;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * This is the base constructor for <i>contigious</i> spaces
   * (i.e. those that occupy a single contigious range of virtual
   * memory which is identified at construction time).<p>
   *
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param start The start address of the space in virtual memory
   * @param extent The size of the space in virtual memory, in bytes
   */
  ContiguousSpace(String name, boolean movable, boolean immortal, Address start,
      Extent extent) {
    super(name, movable, immortal, createDescriptor(false, start, extent));
    this.start = start;
    this.extent = extent;
    /* ensure requests are chunk aligned */
    if (extent.NE(chunkAlign(extent, false))) {
      Log.write("Warning: ");
      Log.write(name);
      Log.write(" space request for ");
      Log.write(extent.toLong()); Log.write(" bytes rounded up to ");
      extent = chunkAlign(extent, false);
      Log.write(extent.toLong()); Log.writeln(" extent");
      Log.writeln("(requests should be Space.BYTES_IN_CHUNK aligned)");
    }
    this.extent = extent;

    VM.memory.setHeapRange(getIndex(), start, start.plus(extent));
    Map.insert(start, this.extent, getDescriptor(), this);

    if (DEBUG) {
      Log.write(name); Log.write(" ");
      Log.write(start); Log.write(" ");
      Log.write(start.plus(this.extent)); Log.write(" ");
      Log.writeln(extent.toWord());
    }
  }

  /**
   * Construct a space of a given number of megabytes in size.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  ContiguousSpace(String name, boolean movable, boolean immortal, int mb) {
    this(name, movable, immortal, heapCursor,
         Word.fromIntSignExtend(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent());
    heapCursor = heapCursor.plus(extent);
    if (heapCursor.GT(heapLimit)) {
      Log.write("Out of virtual address space allocating \"");
      Log.write(name); Log.write("\" at ");
      Log.write(heapCursor.minus(extent)); Log.write(" (");
      Log.write(heapCursor); Log.write(" > ");
      Log.write(heapLimit); Log.writeln(")");
      VM.assertions.fail("exiting");
    }
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  ContiguousSpace(String name, boolean movable, boolean immortal, float frac) {
    this(name, movable, immortal, heapCursor, getFracAvailable(frac));
    heapCursor = heapCursor.plus(extent);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  ContiguousSpace(String name, boolean movable, boolean immortal, int mb, boolean top) {
    this(name, movable, immortal,
         Word.fromIntSignExtend(mb).lsh(LOG_BYTES_IN_MBYTE).toExtent(), top);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   *          virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  ContiguousSpace(String name, boolean movable, boolean immortal, float frac,
        boolean top) {
    this(name, movable, immortal, getFracAvailable(frac), top);
  }

  /**
   * This is a private constructor that creates a contigious space at
   * the top or bottom of the available virtual memory, if
   * possible.<p>
   *
   * The caller specifies the size of the region of virtual memory and
   * whether it should be at the top or bottom of the available
   * address space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param movable Are objects in this space movable?
   * @param immortal Are objects in this space immortal (uncollected)?
   * @param bytes The size of the space in virtual memory, in bytes
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  private ContiguousSpace(String name, boolean movable, boolean immortal, Extent bytes,
      boolean top) {
    this(name, movable, immortal, (top) ? heapLimit.minus(bytes) : HEAP_START,
        bytes);
    if (top) { // request for the top of available memory
      /*      if (heapLimit.NE(HEAP_END)) {
        Log.write("Unable to satisfy virtual address space request \"");
        Log.write(name); Log.write("\" at ");
        Log.writeln(heapLimit);
        VM.assertions.fail("exiting");
	} */
      heapLimit = heapLimit.minus(extent);
    } else { // request for the bottom of available memory
      if (heapCursor.GT(HEAP_START)) {
        Log.write("Unable to satisfy virtual address space request \"");
        Log.write(name); Log.write("\" at ");
        Log.writeln(heapCursor);
        VM.assertions.fail("exiting");
      }
      heapCursor = heapCursor.plus(extent);
    }
  }


  /****************************************************************************
   *
   * Accessor methods
   */

  /** Start getter @return The start address of this space */
  public final Address getStart() { return start; }

  /** Extent getter @return The size (extent) of this space */
  public final Extent getExtent() { return extent; }

  /****************************************************************************
   *
   * Debugging / printing
   */

  protected void printVMRange() {
    Log.write(start);
    Log.write("->");
    Log.writeln(start.plus(extent.minus(1)));
  }

  /**
   * {@inheritDoc}
   */
  protected void ensureMapped() {
    Mmapper.ensureMapped(start, extent.toInt()>>LOG_BYTES_IN_PAGE);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Align an extent to a space chunk
   *
   * @param bytes The extent to be aligned
   * @param down If true the address will be rounded down, otherwise
   * it will rounded up.
   */
  private static Extent chunkAlign(Extent bytes, boolean down) {
    if (!down) bytes = bytes.plus(BYTES_IN_CHUNK - 1);
    return bytes.toWord().rshl(LOG_BYTES_IN_CHUNK).lsh(LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Initialize/create the descriptor for this space
   *
   * @param shared True if this is a shared (discontigious) space
   */
  private static int createDescriptor(boolean shared, Address start, Extent extent) {
    if (shared)
      return SpaceDescriptor.createDescriptor();
    else
      return SpaceDescriptor.createDescriptor(start, start.plus(extent));
  }

  /**
   * Convert a fraction into a number of bytes according to the
   * fraction of available bytes.
   *
   * @param frac The fraction of avialable virtual memory desired
   * @return The corresponding number of bytes, chunk-aligned.
   */
  private static Extent getFracAvailable(float frac) {
    long bytes = (long) (frac * AVAILABLE_BYTES.toLong());
    Word mb = Word.fromIntSignExtend((int) (bytes >> LOG_BYTES_IN_MBYTE));
    Extent rtn = mb.lsh(LOG_BYTES_IN_MBYTE).toExtent();
    return chunkAlign(rtn, false);
  }
}
