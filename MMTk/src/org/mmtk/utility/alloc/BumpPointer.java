/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import org.mmtk.vm.gcspy.AbstractDriver;

/**
 * This class implements a simple bump pointer allocator.  The
 * allocator operates in <code>BLOCK</code> sized units.  Intra-block
 * allocation is fast, requiring only a load, addition comparison and
 * store.  If a block boundary is encountered the allocator will
 * request more memory (virtual and actual).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class BumpPointer extends Allocator 
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource from which this bump
   * pointer will acquire virtual memory.
   * @param mr The memory resource from which this bump pointer will
   * acquire memory.
   */
  public BumpPointer(Space space) {
    this.space = space;
    reset();
  }

  public void reset () {
    cursor = Address.zero();
    limit = Address.zero();
  }

  /**
   * Re-associate this bump pointer with a different virtual memory
   * resource.  Reset the bump pointer so that it will use this virtual
   * memory resource on the next call to <code>alloc</code>.
   *
   * @param vmr The virtual memory resouce with which this bump
   * pointer is to be associated.
   */
  public void rebind(Space space) {
    reset();
    this.space = space;
  }

  /**
   * Allocate space for a new object.  This is frequently executed code and 
   * the coding is deliberaetly sensitive to the optimizing compiler.
   * After changing this, always check the IR/MC that is generated.
   *
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment 
   * @return The address of the first byte of the allocated region
   */
  final public Address alloc(int bytes, int align, int offset) 
    throws InlinePragma {
    Address oldCursor = alignAllocation(cursor, align, offset);
    Address newCursor = oldCursor.add(bytes);
      if (newCursor.GT(limit))
      return allocSlow(bytes, align, offset);
    cursor = newCursor;
    //    Log.write("a["); Log.write(oldCursor); Log.writeln("]");
    return oldCursor;
  }

  final protected Address allocSlowOnce(int bytes, int align, int offset, 
                                           boolean inGC) {
    Extent chunkSize = Word.fromIntZeroExtend(bytes).add(CHUNK_MASK).and(CHUNK_MASK.not()).toExtent();
    Address start;
    start = space.acquire(Conversions.bytesToPages(chunkSize));
    if (start.isZero())
      return start;

    // check for (dis)contiguity with previous chunk
    if (limit.NE(start)) cursor = start;
    limit = start.add(chunkSize);
    return alloc(bytes, align, offset);
  }

  public void show() {
    Log.write("cursor = "); Log.write(cursor);
    Log.write(" limit = "); Log.writeln(limit);
  }

  /**
   * Gather data for GCSpy
   * @param event The GCSpy event
   * @param driver the GCSpy driver for this space
   */
  public void gcspyGatherData(int event, AbstractDriver driver) {
    //    vmResource.gcspyGatherData(event, driver);
  }

  /****************************************************************************
   *
   * Instance variables
   */
  private Address cursor;
  private Address limit;
  private Space space;

  /****************************************************************************
   *
   * Final class variables (aka constants)
   *
   * Must ensure the bump pointer will go through slow path on (first)
   * alloc of initial value
   */
  private static final int LOG_CHUNK_SIZE = LOG_BYTES_IN_PAGE + 3;
  private static final Word CHUNK_MASK = Word.one().lsh(LOG_CHUNK_SIZE).sub(Word.one());
}
