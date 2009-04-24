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

  /**
   * Clear the contents of the table. This is called when finalization is
   * disabled to make it easier for VMs to change this setting at runtime.
   */
  public void clear() {
  }

  /**
   * Scan through the list of references.
   *
   * @param trace the thread local trace element.
   * @param nursery true if it is safe to only scan new references.
   */
  public void scan(TraceLocal trace, boolean nursery) {
    Assert.notImplemented();
  }

  /**
   * Iterate over and forward entries in the table.
   */
  public void forward(TraceLocal trace, boolean nursery) {
    Assert.notImplemented();
  }
}
