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
package org.mmtk.plan.refcount.cd;

import org.vmmagic.pragma.*;
/**
 * This class implements the abstract <i>per-mutator thread</i> 
 * behavior for a cycle detector. 
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
