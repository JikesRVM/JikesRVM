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
package org.mmtk.harness;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The collection of mutators
 */
public final class Mutators {

  /** Registered mutators */
  private static final ArrayList<Mutator> mutators = new ArrayList<Mutator>();

  private static int nextId = 0;

  /**
   * Register a mutator, reserving a slot in the list
   * @return The reserved slot number
   */
  static synchronized int registerMutator() {
    return nextId++;
  }

  /**
   * Complete the registration of a mutator, by inserting the initialized
   * object in the allocated slot.
   * @param id
   * @param m
   */
  static synchronized void set(Mutator m) {
    mutators.add(m);
  }

  /**
   * Return the collection of valid mutators, as a blocking queue
   * (so that it's synchronized - don't call 'take' on an empty queue ...)
   * @return The non-null mutators
   */
  public static synchronized BlockingQueue<Mutator> getAll() {
    BlockingQueue<Mutator> result = new ArrayBlockingQueue<Mutator>(mutators.size()+1);
    for (Mutator m : mutators) {
      if (m != null) {
        result.add(m);
      }
    }
    return result;
  }

  /**
   * @return the number of valid mutators.
   */
  public static int count() {
    return getAll().size();
  }
}
