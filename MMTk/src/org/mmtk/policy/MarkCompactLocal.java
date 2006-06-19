/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.policy;

import org.mmtk.plan.markcompact.MC;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Memory;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements unsynchronized (local) elements of a
 * sliding mark-compact collector. Allocation is via the bump pointer 
 * (@see BumpPointer). 
 *
 * @see BumpPointer
 * @see MarkCompactSpace 
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public final class MarkCompactLocal extends BumpPointer implements Uninterruptible {
  public final static String Id = "$Id$"; 

  /**
   * Constructor
   *
   * @param space The space to bump point into.
   */
  public MarkCompactLocal(MarkCompactSpace space) {
    super(space, true);
  }
  
  /**
   * Perform the compacting phase of the collection.
   */
  public void compact() {
    /* Has this allocator ever allocated anything? */
    if (initialRegion.isZero()) return;

    /* Loop through active regions or until the last region */
    Address start = initialRegion;
    Address allocStart = initialRegion;
    Address allocEnd = initialRegion.plus(REGION_LIMIT_OFFSET).loadAddress();
    Address allocCursor = allocStart.plus(DATA_START_OFFSET);

    /* Keep track of which regions are being used */
    int oldPages = 0;
    int newPages = 0;

    while(!start.isZero()) {      
      /* Get the end of this region */
      Address end = start.plus(REGION_LIMIT_OFFSET).loadAddress();
      Address dataEnd = start.plus(DATA_END_OFFSET).loadAddress();
      Address nextRegion = start.plus(NEXT_REGION_OFFSET).loadAddress();
      oldPages += Conversions.bytesToPages(end.diff(start).plus(BYTES_IN_ADDRESS));

      /* dataEnd = zero represents the current region. */
      Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
      ObjectReference current =
        ObjectModel.getObjectFromStartAddress(start.plus(DATA_START_OFFSET));
      
      while (ObjectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
        ObjectReference next = ObjectModel.getNextObject(current);

        ObjectReference copyTo = MarkCompactSpace.getForwardingPointer(current);
        
        if (!copyTo.isNull() && Space.isInSpace(MC.MARK_COMPACT, copyTo)) {
          if (Assert.VERIFY_ASSERTIONS) Assert._assert(!MarkCompactSpace.isMarked(current));
          // To be copied.
          if (copyTo.toAddress().GT(allocEnd) || copyTo.toAddress().LT(allocStart)) {
            // changed regions.
            
            Memory.zero(allocCursor, allocEnd.diff(allocCursor).toWord().toExtent().plus(BYTES_IN_ADDRESS));
             
            allocStart.store(allocCursor, DATA_END_OFFSET);
            allocStart = allocStart.plus(NEXT_REGION_OFFSET).loadAddress();
            allocEnd = allocStart.plus(REGION_LIMIT_OFFSET).loadAddress();
            allocCursor = allocStart.plus(DATA_START_OFFSET);
            
            newPages += Conversions.bytesToPages(allocEnd.diff(allocStart).plus(BYTES_IN_ADDRESS));
            
            if (Assert.VERIFY_ASSERTIONS) {
              Assert._assert(allocCursor.LT(allocEnd) && allocCursor.GE(allocStart));
            }
          }
          Address newAllocCursor = ObjectModel.copyTo(current, copyTo, allocCursor);
          allocCursor = newAllocCursor;
          MarkCompactSpace.setForwardingPointer(copyTo, ObjectReference.nullReference());
        }
        current = next;
      }
      if (dataEnd.isZero()) {
        break;
      }
      start = nextRegion;
    }
    Extent zeroBytes = allocEnd.diff(allocCursor).toWord().toExtent().plus(BYTES_IN_ADDRESS);
    Memory.zero(allocCursor, zeroBytes);
    
    allocStart.store(Address.zero(), DATA_END_OFFSET);
    region = allocStart;
    cursor = allocCursor;
    limit = allocEnd;
    if (oldPages > newPages) {
      ((MarkCompactSpace)space).unusePages((oldPages - newPages));
    }
    
    // Zero during GC to help debugging.
    allocStart = allocStart.loadAddress(NEXT_REGION_OFFSET);
    while(!allocStart.isZero()) {
      allocStart.store(Address.zero(), DATA_END_OFFSET);
      if (Assert.VERIFY_ASSERTIONS) {
        Address low = allocStart.plus(DATA_START_OFFSET);
        Extent size = allocStart.loadAddress(REGION_LIMIT_OFFSET).diff(allocStart).toWord().toExtent().minus(2 * BYTES_IN_ADDRESS);
        Memory.zero(low, size);
      }
      allocStart = allocStart.loadAddress(NEXT_REGION_OFFSET);
    }
  }
  
  /**
   * Perform a linear scan through the objects allocated by this bump pointer,
   * calculating where each live object will be post collection. 
   */
  public void calculateForwardingPointers() {
    /* Has this allocator ever allocated anything? */
    if (initialRegion.isZero()) return;

    /* Loop through active regions or until the last region */
    Address start = initialRegion;
    Address allocStart = initialRegion;
    Address allocDataEnd = initialRegion.plus(DATA_END_OFFSET).loadAddress();
    Address allocLimit = (allocDataEnd.isZero() ? cursor : allocDataEnd);
    Address allocCursor = start.plus(DATA_START_OFFSET);
    
    while(!start.isZero()) {      
      /* Get the end of this region */
      Address dataEnd = start.plus(DATA_END_OFFSET).loadAddress();

      /* dataEnd = zero represents the current region. */
      Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
      ObjectReference current =
        ObjectModel.getObjectFromStartAddress(start.plus(DATA_START_OFFSET));
      
      while (ObjectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
        ObjectReference next = ObjectModel.getNextObject(current);

        if (MarkCompactSpace.toBeCompacted(current)) {
          if (Assert.VERIFY_ASSERTIONS) {
            Assert._assert(MarkCompactSpace.getForwardingPointer(current).isNull());
          }

          // Fake - allocate it.
          int size = ObjectModel.getSizeWhenCopied(current);
          int align = ObjectModel.getAlignWhenCopied(current);
          int offset = ObjectModel.getAlignOffsetWhenCopied(current);
          allocCursor = Allocator.alignAllocationNoFill(allocCursor, align, offset);
          
          boolean sameRegion = allocStart.EQ(start);
          
          if (!sameRegion && allocCursor.plus(size).GT(allocLimit)) {
            allocStart = allocStart.plus(NEXT_REGION_OFFSET).loadAddress();
            allocDataEnd = allocStart.plus(DATA_END_OFFSET).loadAddress();
            allocLimit = (allocDataEnd.isZero() ? cursor : allocDataEnd);
            allocCursor = Allocator.alignAllocationNoFill(allocStart.plus(DATA_START_OFFSET), align, offset);
          }
          
          ObjectReference target = ObjectModel.getReferenceWhenCopiedTo(current, allocCursor);
          if (sameRegion && target.toAddress().GE(current.toAddress())) {
            MarkCompactSpace.setForwardingPointer(current, current);
            allocCursor = ObjectModel.getObjectEndAddress(current);
          } else {
            MarkCompactSpace.setForwardingPointer(current, target);
            allocCursor = allocCursor.plus(size);
          }
        }
        current = next;
      }
      if (dataEnd.isZero()) {
        break;
      }
      start = start.plus(NEXT_REGION_OFFSET).loadAddress(); // Move on to next
    }
  }
  
  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   * @return the maximum region size
   */
  protected Extent maximumRegionSize() { return Extent.fromInt(4 << LOG_CHUNK_SIZE) ; }
}
