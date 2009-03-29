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
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.vmmagic.pragma.*;

/**
 * SemiSpace common constants.
 */
@Uninterruptible
public class SSConstraints extends StopTheWorldConstraints {
  @Override
  public boolean movesObjects() { return true; }
  @Override
  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }
  @Override
  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }
  @Override
  public int numSpecializedScans() { return 1; }
}
