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

import org.vmmagic.pragma.Uninterruptible;

/**
 * A counter that supports atomic increment and reset.
 */
@Uninterruptible
public class SynchronizedCounter extends org.mmtk.vm.SynchronizedCounter {

  /** The current value of the counter */
  private int value;

  @Override
  public synchronized int reset() {
    int old = value;
    value = 0;
    return old;
  }

  @Override
  public synchronized int increment() {
    return value++;
  }

  @Override
  public int peek() {
    return value;
  }
}
