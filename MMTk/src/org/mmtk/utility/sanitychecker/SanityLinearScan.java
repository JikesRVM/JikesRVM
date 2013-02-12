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
package org.mmtk.utility.sanitychecker;

import org.mmtk.utility.alloc.LinearScan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class performs sanity checks for Simple collectors.
 */
@Uninterruptible
final class SanityLinearScan extends LinearScan {

  private final SanityChecker sanityChecker;
  public SanityLinearScan(SanityChecker sanityChecker) {
    this.sanityChecker = sanityChecker;
  }

  @Override
  public void scan(ObjectReference object) {
    sanityChecker.scanProcessObject(object);
  }
}
