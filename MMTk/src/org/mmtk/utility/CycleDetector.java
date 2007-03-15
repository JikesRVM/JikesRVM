/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility;

import org.mmtk.utility.options.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 * @author Steve Blackburn
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
