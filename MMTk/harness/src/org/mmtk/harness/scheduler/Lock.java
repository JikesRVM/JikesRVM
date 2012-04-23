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
package org.mmtk.harness.scheduler;

public abstract class Lock extends org.mmtk.vm.Lock {

  /** The name of this lock */
  protected String name;

  @Override
  public abstract void release();

  @Override
  public abstract void check(int w);

  @Override
  public abstract void acquire();

  /** The current holder of the lock */
  protected Thread holder;

  public Lock(String name) {
    this.name = name;
  }

  /**
   * Set the name of this lock instance
   *
   * @param str The name of the lock (for error output).
   */
  @Override
  public void setName(String str) {
    this.name = str;
  }

}
