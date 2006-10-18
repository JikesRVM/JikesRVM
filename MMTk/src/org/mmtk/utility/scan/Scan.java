/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
//$Id$

package org.mmtk.utility.scan;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TraceStep;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning of objects (scalar and array)
 * 
 * @author Robin Garner
 * @author Andrew Gray
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class Scan implements Uninterruptible {
  /**
   * Scan a object, processing each pointer field encountered.
   * 
   * @param trace The trace to use when scanning.
   * @param object The object to be scanned.
   */
  public static void scanObject(TraceStep trace,
                                ObjectReference object) throws InlinePragma {
    MMType type = VM.objectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        trace.traceObjectLocation(slot);
      }
    } else
      VM.scanning.scanObject(trace, object);
  }

  /**
   * Scan a object, pre-copying each child object encountered.
   * 
   * @param trace The trace to use when precopying.
   * @param object The object to be scanned.
   */
  public static void precopyChildren(TraceLocal trace, ObjectReference object)
      throws InlinePragma {
    MMType type = VM.objectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        trace.precopyObjectLocation(slot);
      }
    } else
      VM.scanning.precopyChildren(trace, object);
  }
}
