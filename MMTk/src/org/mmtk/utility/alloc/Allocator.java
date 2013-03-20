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
package org.mmtk.utility.alloc;

import org.mmtk.vm.Lock;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.*;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract base class provides the basis for processor-local
 * allocation.  The key functionality provided is the retry mechanism
 * that is necessary to correctly handle the fact that a "slow-path"
 * allocation can cause a GC which violate the uninterruptability assumption.
 * This results in the thread being moved to a different processor so that
 * the allocator object it is using is not actually the one for the processor
 * it is running on.<p>
 *
 * This class also includes functionality to assist allocators with
 * ensuring that requests are aligned according to requests.<p>
 *
 * Failing to handle this properly will lead to very hard to trace bugs
 * where the allocation that caused a GC or allocations immediately following
 * GC are run incorrectly.
 */
@Uninterruptible
public abstract class Allocator implements Constants {

  /** Lock used for out of memory handling */
  private static Lock oomLock = VM.newLock("OOM Lock");
  /** Has an allocation succeeded since the emergency collection? */
  private static volatile boolean allocationSuccess;
  /** Maximum number of failed attempts by a single thread */
  private static int collectionAttempts;

  /**
   * @return a consecutive failure count for any allocating thread.
   */
  public static int determineCollectionAttempts() {
    if (!allocationSuccess) {
      collectionAttempts++;
    } else {
      allocationSuccess = false;
      collectionAttempts = 1;
    }
    return collectionAttempts;
  }

  /**
   * Return the space this allocator is currently bound to.
   *
   * @return The Space.
   */
  protected abstract Space getSpace();

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles). There is also a knownAlignment parameter to
   * allow a more optimised check when the particular allocator in use
   * always aligns at a coarser grain than individual particles, such
   * as some free lists.
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment
   * @param knownAlignment The statically known minimum alignment.
   * @return The aligned up address.
   */
  @Inline
  public static Address alignAllocation(Address region, int alignment, int offset, int knownAlignment, boolean fillAlignmentGap) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(knownAlignment >= MIN_ALIGNMENT);
      VM.assertions._assert(MIN_ALIGNMENT >= BYTES_IN_INT);
      VM.assertions._assert(!(fillAlignmentGap && region.isZero()));
      VM.assertions._assert(alignment <= MAX_ALIGNMENT);
      VM.assertions._assert(offset >= 0);
      VM.assertions._assert(region.toWord().and(Word.fromIntSignExtend(MIN_ALIGNMENT-1)).isZero());
      VM.assertions._assert((alignment & (MIN_ALIGNMENT - 1)) == 0);
      VM.assertions._assert((offset & (MIN_ALIGNMENT - 1)) == 0);
    }

    // No alignment ever required.
    if (alignment <= knownAlignment || MAX_ALIGNMENT <= MIN_ALIGNMENT)
      return region;

    // May require an alignment
    Word mask = Word.fromIntSignExtend(alignment - 1);
    Word negOff = Word.fromIntSignExtend(-offset);
    Offset delta = negOff.minus(region.toWord()).and(mask).toOffset();

    if (fillAlignmentGap && ALIGNMENT_VALUE != 0) {
      if ((MAX_ALIGNMENT - MIN_ALIGNMENT) == BYTES_IN_WORD) {
        // At most a single hole
        if (delta.toInt() == (BYTES_IN_WORD)) {
          region.store(Word.fromIntSignExtend(ALIGNMENT_VALUE));
          region = region.plus(delta);
        return region;
        }
      } else {
        while (delta.toInt() >= (BYTES_IN_WORD)) {
          region.store(Word.fromIntSignExtend(ALIGNMENT_VALUE));
          region = region.plus(BYTES_IN_WORD);
          delta = delta.minus(BYTES_IN_WORD);
        }
      }
    }

    return region.plus(delta);
  }

  /**
   * Fill the specified region with the alignment value.
   *
   * @param start The start of the region.
   * @param end A pointer past the end of the region.
   */
  @Inline
  public static void fillAlignmentGap(Address start, Address end) {
    if ((MAX_ALIGNMENT - MIN_ALIGNMENT) == BYTES_IN_INT) {
      // At most a single hole
      if (!end.diff(start).isZero()) {
        start.store(ALIGNMENT_VALUE);
      }
    } else {
      while (start.LT(end)) {
        start.store(ALIGNMENT_VALUE);
        start = start.plus(BYTES_IN_INT);
      }
    }
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles).
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment
   * @return The aligned up address.
   */
  @Inline
  public static Address alignAllocation(Address region, int alignment, int offset) {
    return alignAllocation(region, alignment, offset, MIN_ALIGNMENT, true);
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a
   * region, that must be at least particle aligned, an alignment
   * request (some power of two number of particles) and an offset (a
   * number of particles).
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment
   * @return The aligned up address.
   */
  @Inline
  public static Address alignAllocationNoFill(Address region, int alignment, int offset) {
    return alignAllocation(region, alignment, offset, MIN_ALIGNMENT, false);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment.
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   */
  @Inline
  public static int getMaximumAlignedSize(int size, int alignment) {
    return getMaximumAlignedSize(size, alignment, MIN_ALIGNMENT);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment.
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   * @param knownAlignment The known minimum alignment. Specifically for use in
   * allocators that enforce greater than particle alignment. It is a <b>precondition</b>
   * that size is aligned to knownAlignment, and that knownAlignment >= MIN_ALGINMENT.
   */
  @Inline
  public static int getMaximumAlignedSize(int size, int alignment, int knownAlignment) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(size == Conversions.roundDown(size, knownAlignment));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(knownAlignment >= MIN_ALIGNMENT);

    if (MAX_ALIGNMENT <= MIN_ALIGNMENT || alignment <= knownAlignment) {
      return size;
    } else {
      return size + alignment - knownAlignment;
    }
  }

  /**
   * Single slow path allocation attempt. This is called by allocSlow.
   *
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @return The start address of the region, or zero if allocation fails
   */
  protected abstract Address allocSlowOnce(int bytes, int alignment, int offset);

  /**
   * <b>Out-of-line</b> slow path allocation. This method forces slow path
   * allocation to be out of line (typically desirable, but not when the
   * calling context is already explicitly out-of-line).
   *
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @return The start address of the region, or zero if allocation fails
   */
  @NoInline
  public final Address allocSlow(int bytes, int alignment, int offset) {
    return allocSlowInline(bytes, alignment, offset);
  }

  /**
   * <b>Inline</b> slow path allocation. This method attempts allocSlowOnce
   * several times, and allows collection to occur, and ensures that execution
   * safely resumes by taking care of potential thread/mutator context affinity
   * changes. All allocators should use this as the trampoline for slow
   * path allocation.
   *
   * @param bytes The size of the allocation request
   * @param alignment The required alignment
   * @param offset The alignment offset
   * @return The start address of the region, or zero if allocation fails
   */
  @Inline
  public final Address allocSlowInline(int bytes, int alignment, int offset) {
    Allocator current = this;
    Space space = current.getSpace();
    boolean emergencyCollection = false;
    while (true) {
      // Information about the previous collection.

      // Try to allocate using the slow path
      Address result = current.allocSlowOnce(bytes, alignment, offset);

      // Collector allocation always succeeds (or fails inside allocSlow).
      if (!VM.activePlan.isMutator()) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!result.isZero());
        return result;
      }

      if (!result.isZero()) {
        // Report allocation success to assist OutOfMemory handling.
        if (!allocationSuccess) {
          oomLock.acquire();
          allocationSuccess = true;
          oomLock.release();
        }
        return result;
      }

      if (emergencyCollection) {
        // Check if we are in an OutOfMemory situation
        oomLock.acquire();
        boolean failWithOOM = !allocationSuccess;
        // This seems odd, but we must allow each OOM to run its course (and maybe give us back memory)
        allocationSuccess = true;
        oomLock.release();
        if (failWithOOM) {
          // Nobody has successfully allocated since an emergency collection: OutOfMemory
          VM.collection.outOfMemory();
          VM.assertions.fail("Not Reached");
          return Address.zero();
        }
      }

      /* This is in case a GC occurs, and our mutator context is stale.
       * In some VMs the scheduler can change the affinity between the
       * current thread and the mutator context. This is possible for
       * VMs that dynamically multiplex Java threads onto multiple mutator
       * contexts, */
      current = VM.activePlan.mutator().getAllocatorFromSpace(space);

      /*
       * Record whether last collection was an Emergency collection.
       * If so, we make one more attempt to allocate before we signal
       * an OOM.
       */
      emergencyCollection = Plan.isEmergencyCollection();
    }
  }
}
