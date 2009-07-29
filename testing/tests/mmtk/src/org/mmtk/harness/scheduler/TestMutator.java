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

public class TestMutator implements Schedulable {

  private final Collection<Object> resultPool;

  private final Collection<Object> items;


  /**
   * Create a test mutator that inserts 'results' into 'resultpool' when it is scheduled,
   * with a yield point between every insertion.
   * @param resultPool
   * @param result
   */
  TestMutator(Collection<Object> resultPool, Collection<Object> items) {
    super();
    this.resultPool = resultPool;
    this.items = items;
  }

  /**
   * @see org.mmtk.harness.scheduler.Schedulable#execute(org.mmtk.harness.lang.Env)
   */
  @Override
  public void execute(Env env) {
    for (Object item : items) {
      resultPool.add(item);
      Scheduler.yield();
    }
  }

}
