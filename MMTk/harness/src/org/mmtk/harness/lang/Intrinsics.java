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
package org.mmtk.harness.lang;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;

/**************************************************************************
 *
 * "built in" intrinsic functions
 *
 */
public class Intrinsics {
  /**
   * Force GC
   * @param env
   * @return
   */
  public static void gc(Env env) {
    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
  }

  /**
   * Return the thread ID
   * @param env
   * @return
   */
  public static int threadId(Env env) {
    return Mutator.current().getContext().getId();
  }

  /**
   * Return the (identity) hash code
   * @param env
   * @param val
   * @return
   */
  public static int hash(Env env, ObjectValue val) {
    return env.hash(val.getObjectValue());
  }

  /**
   * Set the random seed for this thread
   * @param env
   * @param seed
   */
  public static void setRandomSeed(Env env, int seed) {
    env.random().setSeed(seed);
  }

  /**
   * A random integer in the closed interval [low..high].
   * @param env
   * @param low
   * @param high
   * @return
   */
  public static int random(Env env, int low, int high) {
    return env.random().nextInt(high-low+1) + low;
  }

  /**
   * Dump the heap
   */
  public static void heapDump(Env env) {
    Mutator.dumpHeap();
  }

  /**
   * Unit test method for the Intrinsic method
   */
  public static String testMethod(Env env, int x, boolean y, String string, ObjectValue val) {
    return String.format("successfully called testMethod(%d,%b,%s,%s)", x,y,string,val.toString());
  }

}
