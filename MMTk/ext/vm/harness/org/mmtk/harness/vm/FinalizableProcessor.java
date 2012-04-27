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
package org.mmtk.harness.vm;

import org.mmtk.plan.TraceLocal;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class manages finalizable objects.
 */
@Uninterruptible
public final class FinalizableProcessor extends org.mmtk.vm.FinalizableProcessor {

  @Override
  public void clear() {
  }

  @Override
  public void scan(TraceLocal trace, boolean nursery) {
    Assert.notImplemented();
  }

  @Override
  public void forward(TraceLocal trace, boolean nursery) {
    Assert.notImplemented();
  }
}
