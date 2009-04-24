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
package org.mmtk.policy;

import org.mmtk.plan.markcompact.MC;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.vm.VM;

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
 */
@Uninterruptible public final class MarkCompactLocal extends BumpPointer {

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
    int newPages = Conversions.bytesToPages(allocEnd.diff(allocStart).plus(BYTES_IN_ADDRESS));

    while (!start.isZero()) {
      /* Get the end of this region */
      Address end = start.plus(REGION_LIMIT_OFFSET).loadAddress();
      Address dataEnd = start.plus(DATA_END_OFFSET).loadAddress();
      Address nextRegion = start.plus(NEXT_REGION_OFFSET).loadAddress();
      oldPages += Conversions.bytesToPages(end.diff(start).plus(BYTES_IN_ADDRESS));

      /* dataEnd = zero represents the current region. */
      Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
      ObjectReference current =
        VM.objectModel.getObjectFromStartAddress(start.plus(DATA_START_OFFSET));

      while (VM.objectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
        ObjectReference next = VM.objectModel.getNextObject(current);

        ObjectReference copyTo = MarkCompactSpace.getForwardingPointer(current);

        if (!copyTo.isNull() && Space.isInSpace(MC.MARK_COMPACT, copyTo)) {
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!MarkCompactSpace.isMarked(current));
          // To be copied.
          if (copyTo.toAddress().GT(allocEnd) || copyTo.toAddress().LT(allocStart)) {
            // changed regions.

            VM.memory.zero(allocCursor, allocEnd.diff(allocCursor).toWord().toExtent().plus(BYTES_IN_ADDRESS));

            allocStart.store(allocCursor, DATA_END_OFFSET);
            allocStart = allocStart.plus(NEXT_REGION_OFFSET).loadAddress();
            allocEnd = allocStart.plus(REGION_LIMIT_OFFSET).loadAddress();
            allocCursor = allocStart.plus(DATA_START_OFFSET);

            newPages += Conversions.bytesToPages(allocEnd.diff(allocStart).plus(BYTES_IN_ADDRESS));

            if (VM.VERIFY_ASSERTIONS) {
              VM.assertions._assert(allocCursor.LT(allocEnd) && allocCursor.GE(allocStart));
            }
          }
          allocCursor = VM.objectModel.copyTo(current, copyTo, allocCursor);
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
    VM.memory.zero(allocCursor, zeroBytes);

    allocStart.store(Address.zero(), DATA_END_OFFSET);
    region = allocStart;
    cursor = allocCursor;
    updateLimit(allocEnd, region, 0);
    if (oldPages > newPages) {
      ((MarkCompactSpace) space).unusePages((oldPages - newPages));
    }

    // Zero during GC to help debugging.
    allocStart = allocStart.loadAddress(NEXT_REGION_OFFSET);
    while (!allocStart.isZero()) {
      allocStart.store(Address.zero(), DATA_END_OFFSET);
      if (VM.VERIFY_ASSERTIONS) {
        Address low = allocStart.plus(DATA_START_OFFSET);
        Extent size = allocStart.loadAddress(REGION_LIMIT_OFFSET).diff(allocStart).toWord().toExtent().minus(2 * BYTES_IN_ADDRESS);
        VM.memory.zero(low, size);
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

    while (!start.isZero()) {
      /* Get the end of this region */
      Address dataEnd = start.plus(DATA_END_OFFSET).loadAddress();

      /* dataEnd = zero represents the current region. */
      Address currentLimit = (dataEnd.isZero() ? cursor : dataEnd);
      ObjectReference current =
        VM.objectModel.getObjectFromStartAddress(start.plus(DATA_START_OFFSET));

      while (VM.objectModel.refToAddress(current).LT(currentLimit) && !current.isNull()) {
        ObjectReference next = VM.objectModel.getNextObject(current);

        if (MarkCompactSpace.toBeCompacted(current)) {
          if (VM.VERIFY_ASSERTIONS)
            VM.assertions._assert(MarkCompactSpace.getForwardingPointer(current).isNull());

          // Fake - allocate it.
          int size = VM.objectModel.getSizeWhenCopied(current);
          int align = VM.objectModel.getAlignWhenCopied(current);
          int offset = VM.objectModel.getAlignOffsetWhenCopied(current);
          allocCursor = Allocator.alignAllocationNoFill(allocCursor, align, offset);

          boolean sameRegion = allocStart.EQ(start);

          if (!sameRegion && allocCursor.plus(size).GT(allocLimit)) {
            allocStart = allocStart.plus(NEXT_REGION_OFFSET).loadAddress();
            allocDataEnd = allocStart.plus(DATA_END_OFFSET).loadAddress();
            allocLimit = (allocDataEnd.isZero() ? cursor : allocDataEnd);
            allocCursor = Allocator.alignAllocationNoFill(allocStart.plus(DATA_START_OFFSET), align, offset);
          }

          ObjectReference target = VM.objectModel.getReferenceWhenCopiedTo(current, allocCursor);
          if (sameRegion && target.toAddress().GE(current.toAddress())) {
            MarkCompactSpace.setForwardingPointer(current, current);
            allocCursor = VM.objectModel.getObjectEndAddress(current);
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
   * Some pages are about to be re-used to satisfy a slow path request.
   * @param pages The number of pages.
   */
  protected void reusePages(int pages) {
    ((MarkCompactSpace)space).reusePages(pages);
  }

  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   * @return the maximum region size
   */
  protected Extent maximumRegionSize() { return Extent.fromIntZeroExtend(4 << LOG_BLOCK_SIZE) ; }
}
