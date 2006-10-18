/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */

package org.mmtk.plan.refcount;

import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a dec-buffer for a reference counting collector
 * 
 * @see org.mmtk.plan.TraceStep
 * 
 * $Id: $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.7 $
 * @date $Date: 2006/06/21 07:38:14 $
 */
public final class DecBuffer extends ObjectReferenceBuffer implements Constants, Uninterruptible {
  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param trace The shared deque that is used.
   */
  public DecBuffer(SharedDeque queue) {
    super("dec", queue);
  }
  
  
  /**
   * This is the method that ensures 
   * 
   * @param object The object to process.
   */
  protected void process(ObjectReference object) throws InlinePragma {
    if (RCBase.isRCObject(object)) {
      push(object);
    }
  }
}