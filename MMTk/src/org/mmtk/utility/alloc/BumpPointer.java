/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * $Id$
 * 
 * @author Daniel Frampton
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class BumpPointer extends Allocator 
  implements Constants, Uninterruptible {
  
  /****************************************************************************
   *
   *	 Class variables
   */	
  
  // Chunk size defines slow path periodicity.
  protected static final int LOG_CHUNK_SIZE = LOG_BYTES_IN_PAGE + 3;
  protected static final Word CHUNK_MASK = Word.one().lsh(LOG_CHUNK_SIZE).sub(Word.one());	

  // Offsets into header
  protected static final Offset REGION_LIMIT_OFFSET = Offset.zero();
  protected static final Offset NEXT_REGION_OFFSET = REGION_LIMIT_OFFSET.add(BYTES_IN_ADDRESS);
  protected static final Offset DATA_END_OFFSET = NEXT_REGION_OFFSET.add(BYTES_IN_ADDRESS);

  // Data must start particle-aligned.
  protected static final Offset DATA_START_OFFSET   = alignAllocationNoFill(
      Address.zero().add(DATA_END_OFFSET.add(BYTES_IN_ADDRESS)), 
      MIN_ALIGNMENT, 0).toWord().toOffset();
 
  /****************************************************************************
   *
   *	 Instance variables
   */	
  protected Address cursor;        // insertion point
  protected Address limit;         // current sentinal for bump pointer
  protected Space space;           // space this bump pointer is associated with
  protected Address initialRegion; // first contigious region
  protected final boolean allowScanning; // linear scanning is permitted if true
  protected Address region;        // current contigious region


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
   * Reset the allocator. Note that this does not reset the space.
   * This is must be done by the caller.
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
    return oldCursor;
  }

  /**
   * Allocation slow path (called by superclass when slow path is
   * actually taken.  This is necessary (rather than a direct call
   * from the fast path) because of the possibility of a thread switch
   * and corresponding re-association of bump pointers to kernel
   * threads.
   *  
   * @param bytes The number of bytes allocated
   * @param align The requested alignment
   * @param offset The offset from the alignment 
   * @param inGC Was the request made from within GC?
   * @return The address of the first byte of the allocated region or
   * zero on failure
   */
  final protected Address allocSlowOnce(int bytes, int align, int offset, 
                                        boolean inGC) {
    /* Check if we already have a chunk to use */
    if (allowScanning && !region.isZero()) {
      Address nextRegion = region.loadAddress(NEXT_REGION_OFFSET);
      if (!nextRegion.isZero()) {
        region.add(DATA_END_OFFSET).store(cursor);
        region = nextRegion;
        cursor = nextRegion.add(DATA_START_OFFSET);
        limit = nextRegion.loadAddress(REGION_LIMIT_OFFSET);
        nextRegion.store(Address.zero(), DATA_END_OFFSET);
        Memory.zero(cursor, limit.diff(cursor).toWord().toExtent().add(BYTES_IN_ADDRESS));
        
        ((MarkCompactSpace)space).reusePages(Conversions.bytesToPages(limit.diff(region).add(BYTES_IN_ADDRESS)));
        
        return alloc(bytes, align, offset);
      }
    }
    
    /* Aquire space, chunk aligned, that can accomodate the request */
    Extent chunkSize = Word.fromIntZeroExtend(bytes).add(CHUNK_MASK)
                       .and(CHUNK_MASK.not()).toExtent();
    Address start = space.acquire(Conversions.bytesToPages(chunkSize));

    if (start.isZero()) return start; // failed allocation

    if (!allowScanning) { // simple allocator
      if (start.NE(limit)) cursor = start;
      limit = start.add(chunkSize);
    } else                // scannable allocator
      updateMetaData(start, chunkSize);

    return alloc(bytes, align, offset);
  }

  /**
   * Update the metadata to reflect the addition of a new region.
   * 
   * @param start The start of the new region
   * @param size The size of the new region (rounded up to chunk-alignment)
   */
  private void updateMetaData(Address start, Extent size)
    throws InlinePragma {
    if (initialRegion.isZero()) {
      /* this is the first allocation */
      initialRegion = start;
      region = start;
      cursor = region.add(DATA_START_OFFSET);
    } else if (limit.add(BYTES_IN_ADDRESS).NE(start) 
        || region.diff(start.add(size)).toWord().toExtent()
        .GT(maximumRegionSize())) {
      /* non contiguous or over-size, initialize new region */
      region.add(NEXT_REGION_OFFSET).store(start);
      region.add(DATA_END_OFFSET).store(cursor);
      region = start;
      cursor = start.add(DATA_START_OFFSET);
    }
    limit = start.add(size.sub(BYTES_IN_ADDRESS)); // skip over region limit
    region.add(REGION_LIMIT_OFFSET).store(limit);
  }

  /**
   * Perform a linear scan through the objects allocated by this bump pointer.
   *
   * @param scanner The scan object to delegate scanning to.
   */
  public void linearScan(LinearScan scanner) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(allowScanning);
    /* Has this allocator ever allocated anything? */
    if (initialRegion.isZero()) return;

    /* Loop through active regions or until the last region */
    Address start = initialRegion;
    while(!start.isZero()) {      
      scanRegion(scanner, start); 	                   // Scan this region
      start = start.add(NEXT_REGION_OFFSET).loadAddress(); // Move on to next
    }
  }

  /**
   * Perform a linear scan through a single contigious region
   *
   * @param scanner The scan object to delegate to.
   * @param start The start of this region
   */
  private void scanRegion(LinearScan scanner, Address start) 
    throws InlinePragma {
    /* Get the end of this region */
    Address dataEnd = start.add(DATA_END_OFFSET).loadAddress();

    /* dataEnd = zero represents the current region. */
    Address oldCursor = cursor;
    Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
    ObjectReference current =
      ObjectModel.getObjectFromStartAddress(start.add(DATA_START_OFFSET));

    while (ObjectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
      ObjectReference next = ObjectModel.getNextObject(current);
      scanner.scan(current);  // Scan this object.
      current = next;
    }
    
  }

  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   * @return the maximum region size
   */
  protected Extent maximumRegionSize() { return Extent.max(); }

  /** @return the current cursor value */
  public Address getCursor() { return cursor; }
  /** @return the space associated with this bump pointer */
  public Space getSpace() { return space; }

  /**
   * Print out the status of the allocator (for debugging)
   */
  public void show() {
    Log.write("cursor = "); Log.write(cursor);
    if (allowScanning) {
      Log.write(" region = "); Log.write(region);
    }
    Log.write(" limit = "); Log.writeln(limit);
  }
  
  public void showRegions() {
    Address current = initialRegion;
    
    Log.writeln("REGION      LIMIT      DATAEND");
    Log.writeln("=========== ========== ==========");
    Log.write(current);
    
    while(!current.isZero()) {
      Log.write(" ");
      Log.write(current.loadAddress(REGION_LIMIT_OFFSET));
      Log.write(" ");
      Log.writeln(current.loadAddress(DATA_END_OFFSET));
      current = current.loadAddress(NEXT_REGION_OFFSET);
      Log.write(current);
    }
    
    Log.writeln();
    Log.write("REGION: ");Log.write(region);
    Log.write(" CURSOR: ");Log.write(cursor);
    Log.write(" LIMIT: ");Log.writeln(limit);
    
  }
}
