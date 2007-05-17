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

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * for a null cycle detector.
 */
@Uninterruptible public final class NullCDCollector extends CDCollector {
  /**
   * Buffer an object after a successful update when shouldBufferOnDecRC
   * returned true.
   *  
   * @param object The object to buffer.
   */
  public void bufferOnDecRC(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions.fail("Null CD should never bufferOnDecRC");
    }
  }
}
