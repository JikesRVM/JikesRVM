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

package org.mmtk.vm;

import org.mmtk.policy.Space;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

/**
 * Event generation interface for MMTk.
 */
@Uninterruptible
public abstract class MMTk_Events {
  public abstract void tracePageAcquired(Space space, Address startAddress, int numPages);

  public abstract void tracePageReleased(Space space, Address startAddress, int numPages);

  public abstract void heapSizeChanged(Extent heapSize);

}
