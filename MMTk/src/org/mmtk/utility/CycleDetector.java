/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility;

import org.mmtk.utility.options.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 */
@Uninterruptible abstract class CycleDetector {

  static {
    Options.cycleFilterThreshold = new CycleFilterThreshold();
    Options.cycleMetaDataLimit = new CycleMetaDataLimit();
    Options.cycleTriggerThreshold = new CycleTriggerThreshold();
  }

  abstract boolean collectCycles(boolean primary, boolean time);
  abstract void possibleCycleRoot(ObjectReference object);
}
