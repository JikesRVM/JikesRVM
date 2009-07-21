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
package org.mmtk.harness.scheduler.javathreads;

/**
 * Rendezvous of all collector threads in the Java threading model.
 *
 * Each rendezvous is mediated by a separate Rendezvous object, 'current'.  The
 * first thread to arrive will find this static field null, and create the
 * current Rendezvous object.  Subsequent threads will rendezvous on the existing object.
 *
 * The last thread to arrive clears the 'current' field, ready for the next
 * rendezvous to start.
 */
final class Rendezvous {

  /**
   * The current active rendezvous.  If this is null, the most recent rendezvous
   * (if any) has reached its quota, and the next thread to arrive will start
   * a new rendezvous.
   */
  private static Rendezvous current = null;

  /**
   * Return the current rendezvous, creating a new one if required.
   * @param where
   * @return
   */
  private static synchronized Rendezvous current(int where) {
    if (current == null) {
      current = new Rendezvous(where);
    } else {
      if (where != current.where) {
        /* Logic error - this should never happen */
        throw new RuntimeException(String.format("Arriving at barrier %d when %d is active",
            where,current.where));
      }
    }
    return current;
  }

  /**
   * Clear the <code>current</code> field atomically.
   */
  private static synchronized void clearCurrent() {
    current = null;
  }

  /**
   * Create a new rendezvous with the given identifier
   * @param where The rendezvous identifier
   */
  private Rendezvous(int where) {
    this.where = where;
  }

  /** The rendezvous identifier */
  private final int where;

  /** The rank that was given to the last thread to arrive at the rendezvous */
  private int currentRank = 0;

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  private synchronized int rendezvous() {
    int rank = ++currentRank;
    if (currentRank == org.mmtk.vm.VM.activePlan.collectorCount()) {
      /* This is no longer the current barrier */
      clearCurrent();
      notifyAll();
    } else {
      while (currentRank != org.mmtk.vm.VM.activePlan.collectorCount()) {
        try {
          /* Wait for the remaining collectors to arrive */
          wait();
        } catch (InterruptedException ie) {
        }
      }
    }
    return rank;
  }

  /**
   * Dispatch to the current rendezvous object
   * @param where
   * @return
   */
  static int rendezvous(int where) {
    return current(where).rendezvous();
  }
}
