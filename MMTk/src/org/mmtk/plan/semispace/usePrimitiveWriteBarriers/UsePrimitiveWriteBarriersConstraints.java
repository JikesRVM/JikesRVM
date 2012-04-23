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
  @Override
  public boolean needsBooleanWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk boolean arraycopy barriers. */
  @Override
  public boolean booleanBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on bytes. */
  @Override
  public boolean needsByteWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk byte arraycopy barriers. */
  @Override
  public boolean byteBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on chars. */
  @Override
  public boolean needsCharWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk char arraycopy barriers. */
  @Override
  public boolean charBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on shorts. */
  @Override
  public boolean needsShortWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk short arraycopy barriers. */
  @Override
  public boolean shortBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on ints. */
  @Override
  public boolean needsIntWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk int arraycopy barriers. */
  @Override
  public boolean intBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on longs. */
  @Override
  public boolean needsLongWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk long arraycopy barriers. */
  @Override
  public boolean longBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on floats. */
  @Override
  public boolean needsFloatWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk float arraycopy barriers. */
  @Override
  public boolean floatBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on doubles. */
  @Override
  public boolean needsDoubleWriteBarrier() { return true; }

  /** @return True if this Plan can perform bulk double arraycopy barriers. */
  @Override
  public boolean doubleBulkCopySupported() { return true; }

  /** @return True if this Plan requires write barriers on Words. */
  @Override
  public boolean needsWordWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Address's. */
  @Override
  public boolean needsAddressWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Extents. */
  @Override
  public boolean needsExtentWriteBarrier() { return true; }

  /** @return True if this Plan requires write barriers on Offsets. */
  @Override
  public boolean needsOffsetWriteBarrier() { return true; }

}
