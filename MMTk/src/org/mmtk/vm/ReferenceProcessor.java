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
package org.mmtk.vm;

import org.mmtk.plan.TraceLocal;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences.
 */
@Uninterruptible
public abstract class ReferenceProcessor {

  public enum Semantics { SOFT, WEAK, PHANTOM }

  /**
   * Scan through the list of references.
   *
   * @param trace the thread local trace element.
   * @param nursery true if it is safe to only scan new references.
   */
  public abstract void scan(TraceLocal trace, boolean nursery);

  /**
   * Iterate over all references and forward.
   */
  public abstract void forward(TraceLocal trace, boolean nursery);

  /**
   * Return the number of references objects on the queue
   *
   * @return
   */
  public abstract int countWaitingReferences();
}
