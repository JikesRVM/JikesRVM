/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import org.mmtk.vm.gcspy.AbstractDriver;

/**
 * This class implements a bump pointer allocator that allows linearly 
 * scanning through the allocated objects. In order to achieve this in the 
 * face of parallelism it maintains a header at a region (1 or more chunks)
 * granularity. 
 *
 * Intra-block allocation is fast, requiring only a load, addition comparison
 * and store.  If a block boundary is encountered the allocator will
 * request more memory (virtual and actual).
 *
 * In the current implementation the scanned objects maintain affinity 
 * with the thread that allocated the objects in the region. In the future
 * it is anticipated that subclasses should be allowed to choose to improve 
 * load balancing during the parallel scan.
 *
 * Each region is laid out as follows:
 *  
 *  +-------------+-------------+-------------+---------------
 *  | Region  End | Next Region |  Data  End  | Data --> 
 *  +-------------+-------------+-------------+---------------
 *
 * The minimum region size is 32768 bytes, so the 3 or 4 word overhead is 
 * less than 0.05% of all space. 
 *
 * An intended enhancement is to facilitate a reallocation operation
 * where a second cursor is maintained over earlier regions (and at the 
 * limit a lower location in the same region). This would be accompianied
 * with an alternative slow path that would allow reuse of empty regions. 
 *
 * This class relies on the supporting virtual machine implementing the 
 * getNextObject and related operations.
 *
 * @author Daniel Frampton
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class BumpPointer extends Allocator 
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /**
   * Constructor.
   *
   * @param space The space to bump point into.
   * @param allowScanning Allow linear scanning of this region of memory.
   */
  protected BumpPointer(Space space, boolean allowScanning) {
    this.space = space;
    this.allowScanning = allowScanning;
    reset();
  } 
 
  /**
   * Reset the allocator. This assumes that the caller will make the 
   * appropriate calls to also reset the associated space. 
   */ 
  public void reset() {
    cursor = Address.zero();
    limit = Address.zero();
    initialRegion = Address.zero();
    region = Address.zero();
  }

  /**
   * Re-associate this bump pointer with a different space. Also 
   * reset the bump pointer so that it will use the new space
   * on the next call to <code>alloc</code>.
   *
   * @param space The space to associate the bump pointer with.
   */
  public void rebind(Space space) {
    this.reset();
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
    Address oldCursor = alignAllocation(this.cursor, align, offset);
    Address newCursor = oldCursor.add(bytes);
      if (newCursor.GT(this.limit))
      return this.allocSlow(bytes, align, offset);
    this.cursor = newCursor;
    //    Log.write("a["); Log.write(oldCursor); Log.writeln("]");
    return oldCursor;
  }

  final protected Address allocSlowOnce(int bytes, int align, int offset, 
                                           boolean inGC) {
    // Ensure the selected chunk size can accomodate the largest object.
    Extent chunkSize = Word.fromIntZeroExtend(bytes).add(CHUNK_MASK)
                       .and(CHUNK_MASK.not()).toExtent();
    Address start = space.acquire(Conversions.bytesToPages(chunkSize));

    if (start.isZero())
      return start;

    if (!allowScanning) { 
      // simple allocator
      if (start.NE(this.limit)) this.cursor = start;
      this.limit = start.add(chunkSize);

    } else {
      if (initialRegion.isZero()) {
        // first allocation
        this.initialRegion = start;
        this.region = start;
        this.cursor = region.add(DATA_START_OFFSET);
      } else if (this.limit.add(BYTES_IN_ADDRESS).NE(start) 
                 || this.region.diff(start.add(chunkSize)).toWord().toExtent()
                    .GT(maximumRegionSize())) {
        // non contiguous or maximum size, initialize new region
        this.region.add(NEXT_REGION_OFFSET).store(start);
        this.region.add(DATA_END_OFFSET).store(this.cursor);
      
        this.region = start;
        this.cursor = start.add(DATA_START_OFFSET);
      }

      this.limit = start.add(chunkSize.sub(BYTES_IN_ADDRESS));
      this.region.add(REGION_LIMIT_OFFSET).store(this.limit);
    }

    return this.alloc(bytes, align, offset);
  }

  /**
   * Perform a linear scan through the objects allocated by this bump pointer.
   *
   * @param scanner The scan object to delegate to.
   */
  public void linearScan(LinearScan scanner) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(this.allowScanning);

    // Has this allocator ever allocated anything?
    if (initialRegion.isZero()) return;

    Address start = this.initialRegion;
    
    // Loop through active regions or until the last region
    while(!start.isZero()) {
      // Get the end of this region
      Address end = start.add(REGION_LIMIT_OFFSET).loadAddress();
      Address dataEnd = start.add(DATA_END_OFFSET).loadAddress();

      // dataEnd = zero represents the current region.
      Address currentLimit = (dataEnd.isZero() ? this.cursor : dataEnd);
      ObjectReference current =
        ObjectModel.getObjectFromStartAddress(start.add(DATA_START_OFFSET));
      Address currentAddress = ObjectModel.refToAddress(current);

      while (currentAddress.LT(currentLimit)) {
        // Get the next object
        ObjectReference next = ObjectModel.getNextObject(current);

        // scan this object.
        scanner.scan(current);
        current = next;
        currentAddress = ObjectModel.refToAddress(current);
      }

      // Move on to next region
      start = start.add(NEXT_REGION_OFFSET).loadAddress();
    }
  }

  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   */
  protected Extent maximumRegionSize() {
    return Extent.max();
  }

  /**
   * Print out the status of the allocator (for debugging)
   */
  public void show() {
    Log.write("cursor = "); Log.write(cursor);
    if (this.allowScanning) {
      Log.write(" region = "); Log.write(region);
    }
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
  private Address initialRegion;
  private boolean allowScanning;
  private Address region;
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
  private static final Word CHUNK_MASK 
    = Word.one().lsh(LOG_CHUNK_SIZE).sub(Word.one());

  /****************************************************************************
   * 
   * Offsets into header
   */
  private static final Offset REGION_LIMIT_OFFSET 
    = Offset.zero();
  private static final Offset NEXT_REGION_OFFSET  
    = REGION_LIMIT_OFFSET.add(BYTES_IN_ADDRESS);
  private static final Offset DATA_END_OFFSET     
    = NEXT_REGION_OFFSET.add(BYTES_IN_ADDRESS);

  // Data must start particle-aligned.
  private static final Offset DATA_START_OFFSET   = alignAllocation(
    Address.zero().add(DATA_END_OFFSET.add(BYTES_IN_ADDRESS)), 
    BYTES_IN_PARTICLE, 0).toWord().toOffset();
}
