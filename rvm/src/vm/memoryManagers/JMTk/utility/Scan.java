/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
//$Id$

package org.mmtk.utility.scan;

import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Scanning;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning of objects (scalar and array)
 *
 * @author Robin Garner
 * @author Andrew Gray
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */  
public final class Scan implements Uninterruptible {
  /**
   * Scan a object, processing each pointer field encountered. 
   *
   * @param object The object to be scanned.
   */
  public static void scanObject(Address object) throws InlinePragma {
    MMType type = ObjectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        Plan.traceObjectLocation(slot);
      }
    } else
      Scanning.scanObject(object);
  }

  /**
   * Enumerate the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param enum the Enumerate object through which the callback
   * is made
   */
  public static void enumeratePointers(Address object, Enumerate enum) 
    throws InlinePragma {
    MMType type = ObjectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        enum.enumeratePointerLocation(slot);
      }
    } else
      Scanning.enumeratePointers(object, enum);
  }
}
