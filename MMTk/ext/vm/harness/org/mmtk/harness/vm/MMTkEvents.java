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

import org.mmtk.policy.Space;
import org.mmtk.vm.MMTk_Events;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

public final class MMTkEvents extends MMTk_Events {

  @Override
  public void heapSizeChanged(Extent heapSize) {
  }

  @Override
  public void tracePageAcquired(Space space, Address startAddress, int numPages) {
  }

  @Override
  public void tracePageReleased(Space space, Address startAddress, int numPages) {
  }

}
