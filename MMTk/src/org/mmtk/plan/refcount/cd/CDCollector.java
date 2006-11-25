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

import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for a cycle detector.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public abstract class CDCollector {
  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public CDCollector() {
  }
  
  /*****************************************************************************
   * 
   * Collection
   */
  
  /**
   * Perform a collection phase.
   * 
   * @param phaseId Collection phase to execute.
   * @param primary Use this thread to execute any single-threaded collector
   * context actions.
   */
  public boolean collectionPhase(int phaseId, boolean primary) throws InlinePragma {
    return false;
  }
  
  
  /**
   * Buffer an object after a successful update when shouldBufferOnDecRC
   * returned true.
   *  
   * @param object The object to buffer.
   */
  public abstract void bufferOnDecRC(ObjectReference object);
  
  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active cycle detector global instance */
  public static final CDCollector current() throws InlinePragma {
    return ((RCBaseCollector)VM.activePlan.collector()).cycleDetector();
  }
}
