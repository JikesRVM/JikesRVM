/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;
import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract base class provides the basis for processor-local
 * allocation.  The key functionality provided is the retry mechanism
 * that is necessary to correctly handle the fact that a "slow-path"
 * allocation can cause a GC which violate the uninterruptability assumption.
 * This results in the thread being moved to a different processor so that
 * the allocator object it is using is not actually the one for the processor
 * it is running on.
 *
 * This class also includes functionality to assist allocators with
 * ensuring that requests are aligned according to requests.
 *
 * Failing to handle this properly will lead to very hard to trace bugs
 * where the allocation that caused a GC or allocations immediately following
 * GC are run incorrectly.
 *
 * @author Perry Cheng
 * @modified Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */

public abstract class Allocator implements Constants, Uninterruptible {
  public final static String Id = "$Id$";

  /**
   * Maximum number of retries on consecutive allocation failure.
   *
   */
  private static final int MAX_RETRY = 5;

  /**
   * Constructor
   *
   */
  Allocator () {
  }

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
  final public static Address alignAllocation(Address region, int alignment, 
                                             int offset, int knownAlignment)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(knownAlignment >= MIN_ALIGNMENT);
      Assert._assert(MIN_ALIGNMENT >= BYTES_IN_INT);
      Assert._assert(alignment <= MAX_ALIGNMENT);
      Assert._assert(offset >= 0);
      Assert._assert(region.toWord().and(Word.fromIntSignExtend(MIN_ALIGNMENT-1)).isZero());
      Assert._assert((alignment & (MIN_ALIGNMENT-1)) == 0);
      Assert._assert((offset & (MIN_ALIGNMENT-1)) == 0);
    }

    // No alignment ever required.
    if (alignment <= knownAlignment || MAX_ALIGNMENT <= MIN_ALIGNMENT)
      return region; 

    // May require an alignment
    Word mask  = Word.fromIntSignExtend(alignment-1);
    Word negOff= Word.fromIntSignExtend(-offset);
    Offset delta = negOff.sub(region.toWord()).and(mask).toOffset();
    return region.add(delta);
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
  final public static Address alignAllocation(Address region, int alignment, 
                                             int offset) 
    throws InlinePragma {
    return alignAllocation(region, alignment, offset, MIN_ALIGNMENT);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment. 
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   */
  final public static int getMaximumAlignedSize(int size, int alignment) 
    throws InlinePragma {
    return getMaximumAlignedSize(size, alignment, MIN_ALIGNMENT);
  }
  
  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment. 
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   * @param knownAlignment The known minimum alignment. Specifically for use in
   * allocators that enforce greater than particle alignment.
   */
  final public static int getMaximumAlignedSize(int size, int alignment,
                                                int knownAlignment) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(knownAlignment >= MIN_ALIGNMENT);
    if (MAX_ALIGNMENT <= MIN_ALIGNMENT || alignment <= knownAlignment) {
      return size;
    } else {
      return size + alignment - knownAlignment;
    }
  }

  abstract protected Address allocSlowOnce (int bytes, int alignment,
                                               int offset, boolean inGC);

  public Address allocSlow(int bytes, int alignment, int offset) 
    throws NoInlinePragma { 
    return allocSlowBody(bytes, alignment, offset, false);
  }

  public Address allocSlow(int bytes, int alignment, int offset,
                              boolean inGC) 
    throws NoInlinePragma { 
    return allocSlowBody( bytes, alignment, offset, inGC);
  }

  private Address allocSlowBody(int bytes, int alignment, int offset,
                                   boolean inGC) 
    throws InlinePragma { 

    int gcCountStart = Stats.gcCount();
    Allocator current = this;
    for (int i=0; i<MAX_RETRY; i++) {
      Address result = 
        current.allocSlowOnce(bytes, alignment, offset, inGC);
      if (!result.isZero())
        return result;
      current = Plan.getInstance().getOwnAllocator(current);
    }
    Log.write("GC Warning: Possible VM range imbalance - Allocator.allocSlowBody failed on request of ");
    Log.write(bytes);
    Log.write(" on space "); Log.writeln(Plan.getSpaceNameFromAllocatorAnyPlan(this));
    Log.write("gcCountStart = "); Log.writeln(gcCountStart);
    Log.write("gcCount (now) = "); Log.writeln(Stats.gcCount());
    Space.printUsageMB();
    Assert.dumpStack(); 
    Assert.failWithOutOfMemoryError();
    /* NOTREACHED */
    return Address.zero();
  }

}
