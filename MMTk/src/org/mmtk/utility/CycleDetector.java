/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility;

import org.mmtk.utility.options.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract class CycleDetector implements Uninterruptible {
  public final static String Id = "$Id$"; 

  protected static CycleFilterThreshold cycleFilterThreshold;
  protected static CycleMetaDataLimit cycleMetaDataLimit;
  protected static CycleTriggerThreshold cycleTriggerThreshold;

  static {
    cycleFilterThreshold = new CycleFilterThreshold();
    cycleMetaDataLimit = new CycleMetaDataLimit();
    cycleTriggerThreshold = new CycleTriggerThreshold();
  }

  abstract boolean collectCycles(int count, boolean time);
  abstract void possibleCycleRoot(ObjectReference object);
}
