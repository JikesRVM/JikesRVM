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
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a null cycle detector.

 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public final class NullCD extends CD implements Uninterruptible {

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
