/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.refcount.cd;

import org.mmtk.plan.TraceStep;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This trace step is used during trial deletion processing.
 * 
 * $Id: TraceLocal.java,v 1.7 2006/06/21 07:38:14 steveb-oss Exp $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:14 $
 */
public final class TrialDeletionGreyStep extends TraceStep implements Uninterruptible {

  /**
   * Trace a reference during GC.
   * 
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  public void traceObjectLocation(Address objLoc) {
    ObjectReference object = objLoc.loadObjectReference();
    ((TrialDeletionCollector)CDCollector.current()).enumerateGrey(object);
  }
}
