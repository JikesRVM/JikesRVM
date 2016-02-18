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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;

/**
 * The base heap size.  This is an alternative to specifying
 * initHeap and/or maxHeap.  The actual heap size is calculated
 * by multiplying the value of this option by a factor specific
 * to the current plan.
 *
 * <p>The idea is that by specifying a heap size for a script (e.g.
 * the size in which the most space-efficient script (MC at the moment) runs,
 * the script should run on every collector, and should behave in much the same
 * way.
 *
 * <p>If unspecified (value -1), the values of initHeap etc are used.  If specified,
 * it supersedes those values and sets variableSizedHeap to 'false'.
 */
public final class BaseHeap extends org.vmutil.options.PagesOption {
  /**
   * Create the option.
   */
  public BaseHeap() {
    super(Harness.options, "Base Heap",
        "Base Heap Size (scaled for the current plan)",0);
  }

  /** {@inheritDoc} */
  @Override
  protected void validate() {
    failIf(value <= 0, "Must have a positive heap size");
  }
}
