/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.utility.Log;
import org.mmtk.policy.Space;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Constants;
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
  private static int[] idMap;
  private static Space[] spaceMap;

  static {
    idMap = new int[Space.MAX_CHUNKS];
    spaceMap = new Space[Space.MAX_CHUNKS];
  }

  public static void insert(Address start, Extent extent, int id, Space space)
    throws InterruptiblePragma {
    Extent e = Extent.zero();
    while (e.LT(extent)) {
      int index = hashAddress(start.add(e));
      idMap[index] = id;
      spaceMap[index] = space;
      e = e.add(Space.BYTES_IN_CHUNK);
    }
  }

  private static int hashAddress(Address a) throws InlinePragma {
    return a.toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
  }
  
  public static int getIDForObject(Address object) throws InlinePragma {
    int index = hashAddress(ObjectModel.refToAddress(object));
    return Barriers.getArrayNoBarrier(idMap, index);
  }

  public static Space getSpaceForObject(Address object) throws InlinePragma {
    return getSpaceForAddress(ObjectModel.refToAddress(object));
  }

  public static Space getSpaceForAddress(Address address) throws InlinePragma {
    int index = hashAddress(address);
    return (Space) Barriers.getArrayNoBarrier(spaceMap, index);
  }
}
