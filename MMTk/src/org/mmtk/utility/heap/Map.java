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
package org.mmtk.utility.heap;

import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.policy.Space;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the mapping of spaces to virtual memory ranges.<p>
 *
 * Discontigious spaces are currently unsupported.
 */
@Uninterruptible public class Map implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  private static final int[] descriptorMap;
  private static final Space[] spaceMap;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer. Create our two maps
   */
  static {
    descriptorMap = new int[Space.MAX_CHUNKS];
    spaceMap = new Space[Space.MAX_CHUNKS];
  }

  /****************************************************************************
   *
   * Map accesses and insertion
   */

  /**
   * Insert a space and its descriptor into the map, associating it
   * with a particular address range.
   *
   * @param start The start address of the region to be associated
   * with this space.
   * @param extent The size of the region, in bytes
   * @param descriptor The descriptor for this space
   * @param space The space to be associated with this region
   */
  @Interruptible
  public static void insert(Address start, Extent extent, int descriptor,
      Space space) {
    Extent e = Extent.zero();
    while (e.LT(extent)) {
      int index = hashAddress(start.plus(e));
      if (descriptorMap[index] != 0) {
        Log.write("Conflicting virtual address request for space \"");
        Log.write(space.getName()); Log.write("\" at ");
        Log.writeln(start.plus(e));
        Space.printVMMap();
        VM.assertions.fail("exiting");
      }
      descriptorMap[index] = descriptor;
      spaceMap[index] = space;
      e = e.plus(Space.BYTES_IN_CHUNK);
    }
  }

  /**
   * Return the space in which this address resides.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  @Inline
  public static Space getSpaceForAddress(Address address) {
    int index = hashAddress(address);
    return (Space) VM.barriers.getArrayNoBarrier(spaceMap, index);
  }

  /**
   * Return the space descriptor for the space in which this object
   * resides.
   *
   * @param object The object in question
   * @return The space descriptor for the space in which the object
   * resides
   */
  @Inline
  public static int getDescriptorForAddress(Address object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isZero());
    int index = hashAddress(object);
    return VM.barriers.getArrayNoBarrier(descriptorMap, index);
  }

  /**
   * Hash an address to a chunk (this is simply done via bit shifting)
   *
   * @param address The address to be hashed
   * @return The chunk number that this address hashes into
   */
  @Inline
  private static int hashAddress(Address address) {
    return address.toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
  }
}
