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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Common code for the scheduler tests.  Not actually a Junit test suite,
 * but used by several others.
 */
public class SchedulerTestCases {

  /**
   * One thread inserts a single item into the result list
   *
   * Expected result: A list containing the single item.
   *
   * @param a The item
   * @return The result list
   */
  public List<Object> testOneThreadOneItem(Object a) {
    List<Object> results = new ArrayList<Object>(1);
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(a)));
    Scheduler.schedule();
    return results;
  }

  /**
   * One thread inserts two items into the result list
   *
   * Expected result: A list containing the two items in order.
   *
   * @param a The item
   * @return The result list
   */
  public List<Object> testOneThreadTwoItems(Object a, Object b) {
    List<Object> results = new ArrayList<Object>(2);
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(a,b)));
    Scheduler.schedule();
    return results;
  }

  /**
   * Two threads insert a single item each into the result list
   *
   * Expected result: A list containing the two items in a
   * policy/scheduler-dependent order.
   *
   * @param a The item inserted by thread 1
   * @param b The item inserted by thread 2
   * @return The result list
   */
  public List<Object> testTwoThreadsOneItem(Object a, Object b) {
    List<Object> results = Collections.synchronizedList(new ArrayList<Object>(2));
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(a)));
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(b)));
    Scheduler.schedule();
    return results;
  }

  /**
   * Two threads insert two items each into the result list
   *
   * Expected result: A list containing the four items in a
   * policy/scheduler-dependent order.
   * @param items The four items.
   * @return The result list
   */
  public List<Object> testTwoThreadsTwoItems(Object...items) {
    List<Object> results = Collections.synchronizedList(new ArrayList<Object>(4));
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(items[0],items[1])));
    Scheduler.scheduleMutator(new TestMutator(results,Arrays.asList(items[2],items[3])));
    Scheduler.schedule();
    return results;
  }

}
