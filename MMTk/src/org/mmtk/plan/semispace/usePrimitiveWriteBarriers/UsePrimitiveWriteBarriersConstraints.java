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

  @Override
  public boolean needsBooleanWriteBarrier() { return true; }

  @Override
  public boolean booleanBulkCopySupported() { return true; }

  @Override
  public boolean needsByteWriteBarrier() { return true; }

  @Override
  public boolean byteBulkCopySupported() { return true; }

  @Override
  public boolean needsCharWriteBarrier() { return true; }

  @Override
  public boolean charBulkCopySupported() { return true; }

  @Override
  public boolean needsShortWriteBarrier() { return true; }

  @Override
  public boolean shortBulkCopySupported() { return true; }

  @Override
  public boolean needsIntWriteBarrier() { return true; }

  @Override
  public boolean intBulkCopySupported() { return true; }

  @Override
  public boolean needsLongWriteBarrier() { return true; }

  @Override
  public boolean longBulkCopySupported() { return true; }

  @Override
  public boolean needsFloatWriteBarrier() { return true; }

  @Override
  public boolean floatBulkCopySupported() { return true; }

  @Override
  public boolean needsDoubleWriteBarrier() { return true; }

  @Override
  public boolean doubleBulkCopySupported() { return true; }

  @Override
  public boolean needsWordWriteBarrier() { return true; }

  @Override
  public boolean needsAddressWriteBarrier() { return true; }

  @Override
  public boolean needsExtentWriteBarrier() { return true; }

  @Override
  public boolean needsOffsetWriteBarrier() { return true; }

}
