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

final class Rendezvous {

  private static Rendezvous current = null;

  private static synchronized Rendezvous current(int where) {
    if (current == null) {
      current = new Rendezvous(where);
    } else {
      if (where != current.where) {
        throw new RuntimeException(String.format("Arriving at barrier %d when %d is active",
            where,current.where));
      }
    }
    return current;
  }

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
      synchronized(Rendezvous.class) {
        current = null;
      }
      notifyAll();
    } else {
      while (currentRank != org.mmtk.vm.VM.activePlan.collectorCount()) {
        try {
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
