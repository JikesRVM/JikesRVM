/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.utility.Log;
import org.mmtk.policy.Space;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.utility.Constants;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the mapping of spaces to virtual memory ranges.<p>
 *
 * Discontigious spaces are currently unsupported.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Map implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  private static int[] descriptorMap;
  private static Space[] spaceMap;

  /****************************************************************************
   *
   * Initialization
   */
  
  /**
   * Class initializer.  Create our two maps
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
  public static void insert(Address start, Extent extent, int descriptor,
                            Space space) throws InterruptiblePragma {
    Extent e = Extent.zero();
    while (e.LT(extent)) {
      int index = hashAddress(start.add(e));
      if (descriptorMap[index] != 0) {
        Log.write("Conflicting virtual address request for space \"");
        Log.write(space.getName()); Log.write("\" at ");
        Log.writeln(start.add(e));
        Space.printVMMap();
        Assert.fail("exiting");
      }
      descriptorMap[index] = descriptor;
      spaceMap[index] = space;
      e = e.add(Space.BYTES_IN_CHUNK);
    }
  }

  /**
   * Return the space in which this object resides.
   *
   * @param object The object in question
   * @return The space in which the object resides
   */
  public static Space getSpaceForObject(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    return getSpaceForAddress(ObjectModel.refToAddress(object));
  }

  /**
   * Return the space in which this address resides.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  public static Space getSpaceForAddress(Address address) throws InlinePragma {
    int index = hashAddress(address);
    return (Space) Barriers.getArrayNoBarrier(spaceMap, index);
  }

  /**
   * Return the space descriptor for the space in which this object
   * resides.
   *
   * @param object The object in question
   * @return The space descriptor for the space in which the object
   * resides
   */
  public static int getDescriptorForObject(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    int index = hashAddress(ObjectModel.refToAddress(object));
    return Barriers.getArrayNoBarrier(descriptorMap, index);
  }
  
  /**
   * Hash an address to a chunk (this is simply done via bit shifting)
   *
   * @param address The address to be hashed
   * @return The chunk number that this address hashes into
   */
  private static int hashAddress(Address address) throws InlinePragma {
    return address.toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
  }
}
