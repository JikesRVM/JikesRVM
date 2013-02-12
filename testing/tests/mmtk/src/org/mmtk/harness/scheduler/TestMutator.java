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

import java.util.Collection;

import org.mmtk.harness.lang.Env;

public class TestMutator<T> implements Schedulable {

  private final Collection<T> resultPool;

  private final T[] items;


  /**
   * Create a test mutator that inserts 'items' into 'resultpool' when it is scheduled,
   * with a yield point between every insertion.
   * @param resultPool
   * @param items
   */
  TestMutator(Collection<T> resultPool, T... items) {
    this.resultPool = resultPool;
    this.items = items;
  }

  @Override
  public void execute(Env env) {
    for (T item : items) {
      resultPool.add(item);
      Scheduler.yield();
    }
  }

}
