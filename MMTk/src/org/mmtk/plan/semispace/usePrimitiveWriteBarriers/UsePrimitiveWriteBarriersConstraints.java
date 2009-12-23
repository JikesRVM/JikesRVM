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
package org.mmtk.plan.semispace.usePrimitiveWriteBarriers;

import org.mmtk.plan.semispace.SSConstraints;
import org.vmmagic.pragma.*;

/**
 * UsePrimitiveWriteBarriers common constants.
 */
@Uninterruptible
public class UsePrimitiveWriteBarriersConstraints extends SSConstraints {

  /** @return True if this Plan requires write barriers on booleans. */
  public boolean needsBooleanWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk boolean arraycopy barriers. */
  public boolean booleanBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on bytes. */
  public boolean needsByteWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk byte arraycopy barriers. */
  public boolean byteBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on chars. */
  public boolean needsCharWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk char arraycopy barriers. */
  public boolean charBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on shorts. */
  public boolean needsShortWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk short arraycopy barriers. */
  public boolean shortBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on ints. */
  public boolean needsIntWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk int arraycopy barriers. */
  public boolean intBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on longs. */
  public boolean needsLongWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk long arraycopy barriers. */
  public boolean longBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on floats. */
  public boolean needsFloatWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk float arraycopy barriers. */
  public boolean floatBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on doubles. */
  public boolean needsDoubleWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk double arraycopy barriers. */
  public boolean doubleBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on Words. */
  public boolean needsWordWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Address's. */
  public boolean needsAddressWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Extents. */
  public boolean needsExtentWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Offsets. */
  public boolean needsOffsetWriteBarrier() { return true; }

}
