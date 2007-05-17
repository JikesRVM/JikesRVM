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
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a null cycle detector.

 */
@Uninterruptible public final class NullCD extends CD {

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Update the CD section of the RC word when an increment is performed
   * 
   * @param rcWord The refcount word after the increment.
   * @return The updated status after CD modification
   */
  public int notifyIncRC(int rcWord) {
    // Nothing to do;
    return rcWord;
  }

  /**
   * If the reported decrement succeeds, should we buffer the object?
   * 
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public boolean shouldBufferOnDecRC(int rcWord) {
    // Never
    return false;
  }
  
  /**
   * Allow a free of this object, or is it in a CD data structure
   * 
   * @param object The object to check
   * @return True if free is safe
   */
  public boolean allowFree(ObjectReference object) {
    // Always safe
    return true;
  } 
  
  /**
   * Update the header on a buffered dec to non-zero RC
   * 
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int updateHeaderOnBufferedDec(int rcWord) {
    return rcWord;
  }

  /**
   * Update the header on a non-buffered dec to non-zero RC
   * 
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int updateHeaderOnUnbufferedDec(int rcWord) {
    return rcWord;
  }
  
  /**
   * Perform any cycle detector header initialization.
   * 
   * @param typeRef Type information for the object.
   * @param rcWord The refcount work post decrement.
   * @return The updated status after CD modification
   */
  public int initializeHeader(ObjectReference typeRef, int rcWord) {
    return rcWord;
  }
}
