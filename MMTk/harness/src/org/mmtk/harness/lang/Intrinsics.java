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

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.PhantomReferenceValue;
import org.mmtk.harness.lang.runtime.SoftReferenceValue;
import org.mmtk.harness.lang.runtime.WeakReferenceValue;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.harness.vm.Collection;
import org.mmtk.plan.Plan;

/**
 *
 * "built in" intrinsic functions
 *
 * The language interface to these functions is defined in
 * org.mmtk.harness.lang.parser.GlobalDefs
 */
public class Intrinsics {
  /**
   * Force GC
   * @param env Thread-local environment (language-dependent mutator context)
   */
  public static void gc(Env env) {
    Plan.handleUserCollectionRequest();
  }

  /**
   * Return the count of GCs since start of the running script
   * @param env Thread-local environment (language-dependent mutator context)
   */
  public static int gcCount(Env env) {
    return Collection.getGcCount();
  }

  /**
   * Return the thread ID
   * @param env Thread-local environment (language-dependent mutator context)
   * @return the thread ID
   */
  public static int threadId(Env env) {
    return Mutator.current().getContext().getId();
  }

  /**
   * Return the (identity) hash code
   * @param env Thread-local environment (language-dependent mutator context)
   * @param val The object to hash
   * @return the (identity) hash code
   */
  public static int hash(Env env, ObjectValue val) {
    return env.hash(val.getObjectValue());
  }

  /**
   * Set the random number generator seed for this thread
   * @param env Thread-local environment (language-dependent mutator context)
   * @param seed Pseudo-random seed value
   */
  public static void setRandomSeed(Env env, int seed) {
    env.random().setSeed(seed);
  }

  /**
   * A random integer in the closed interval [low..high].
   * @param env Thread-local environment (language-dependent mutator context)
   * @param low Low bound (inclusive)
   * @param high High bound (inclusive)
   * @return A random integer in the closed interval [low..high]
   */
  public static int random(Env env, int low, int high) {
    return env.random().nextInt(high-low+1) + low;
  }

  /**
   * Dump the heap
   * @param env Thread-local environment (language-dependent mutator context)
   */
  public static void heapDump(Env env) {
    Mutator.dumpHeap();
  }

  /**
   * Unit test method for the Intrinsic method
   *
   * @param env Thread-local environment (language-dependent mutator context)
   * @param x An int
   * @param y A boolean
   * @param string A string
   * @param val An object
   * @return The string representation of <code>val</code>
   */
  public static String testMethod(Env env, int x, boolean y, String string, ObjectValue val) {
    return String.format("successfully called testMethod(%d,%b,%s,%s)", x,y,string,val.toString());
  }

  /**
   * @param env Thread-local environment (language-dependent mutator context)
   * @param referent The object to weakly refer to
   * @return The created weak reference value
   *
   */
  public static WeakReferenceValue weakRef(Env env, ObjectValue referent) {
    return new WeakReferenceValue(referent.getObjectValue());
  }

  /**
   * @param env Thread-local environment (language-dependent mutator context)
   * @param referent The object to weakly refer to
   * @return The created weak reference value
   *
   */
  public static SoftReferenceValue softRef(Env env, ObjectValue referent) {
    return new SoftReferenceValue(referent.getObjectValue());
  }

  /**
   * @param env Thread-local environment (language-dependent mutator context)
   * @param referent The object to weakly refer to
   * @return The created weak reference value
   *
   */
  public static PhantomReferenceValue phantomRef(Env env, ObjectValue referent) {
    return new PhantomReferenceValue(referent.getObjectValue());
  }

  /**
   * Dereference a reference type
   * @param env Thread-local environment (language-dependent mutator context)
   * @param value The reference value
   * @return The referent
   */
  public static ObjectValue getReferent(Env env, WeakReferenceValue value) {
    return new ObjectValue(value.getObjectValue());
  }
  /**
   * Dereference a reference type
   * @param env Thread-local environment (language-dependent mutator context)
   * @param value The reference value
   * @return The referent
   */
  public static ObjectValue getReferent(Env env, SoftReferenceValue value) {
    return new ObjectValue(value.getObjectValue());
  }
  /**
   * Dereference a reference type
   * @param env Thread-local environment (language-dependent mutator context)
   * @param value The reference value
   * @return The referent
   */
  public static ObjectValue getReferent(Env env, PhantomReferenceValue value) {
    return new ObjectValue(value.getObjectValue());
  }

  /**
   * Set a command-line option from within a script
   * @param env Thread-local environment (language-dependent mutator context)
   * @param option The command-line option
   */
  public static void setOption(Env env, String option) {
    if (!Harness.options.process(option)) {
      System.err.println("Error processing option "+option);
    }
  }

  /**
   * A synchronization barrier for script-language threads
   * @param env
   * @param name
   * @param threadCount
   * @return
   */
  public static int barrierWait(Env env, String name, int threadCount) {
    return Scheduler.mutatorRendezvous(name, threadCount);
  }
}
