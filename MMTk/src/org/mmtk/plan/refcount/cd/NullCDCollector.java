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

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * for a null cycle detector.
 * 
 * $Id: MSCollector.java,v 1.3 2006/06/21 07:38:15 steveb-oss Exp $
 * 
 * @author Daniel Frampton
 * @version $Revision: 1.3 $
 * @date $Date: 2006/06/21 07:38:15 $
 */
public final class NullCDCollector extends CDCollector 
  implements Uninterruptible {
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
