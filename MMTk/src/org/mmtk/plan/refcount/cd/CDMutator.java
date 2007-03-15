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

import org.vmmagic.pragma.*;
/**
 * This class implements the abstract <i>per-mutator thread</i> 
 * behavior for a cycle detector. 
 * 
 *
 * @author Daniel Frampton
 */
@Uninterruptible public abstract class CDMutator {
  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   * 
   * Initialization
   */

  
  /*****************************************************************************
   * 
   * Collection
   */
  
  /**
   * Perform a mutator collection phase.
   * 
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public boolean collectionPhase(int phaseId) { 
    return false;
  }
  
  /****************************************************************************
   * 
   * Miscellaneous
   */
}
