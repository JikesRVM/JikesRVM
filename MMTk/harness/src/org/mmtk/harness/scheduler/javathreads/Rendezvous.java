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

import java.util.HashMap;
import java.util.Map;

import org.mmtk.vm.VM;

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
  //private static Rendezvous current = null;
  private static final Map<String,Rendezvous> current = new HashMap<String,Rendezvous>();

  /**
   * Return the current rendezvous, creating a new one if required.
   * @param where
   * @return
   */
  private static synchronized Rendezvous current(String where, int expected) {
    Rendezvous cur = current.get(where);
    if (cur == null) {
      cur = new Rendezvous(where,expected);
      current.put(where, cur);
    } else {
      if (!where.equals(cur.where)) {
        /* Logic error - this should never happen */
        throw new RuntimeException(String.format("Arriving at barrier %d when %s is active",
            where,cur.where));
      }
      assert expected == cur.expected :
        "At barrier "+where+", expected="+expected+", but existing barrier expects "+cur.expected;
    }
    return cur;
  }

  /**
   * Clear the <code>current</code> field atomically.
   * @param where TODO
   */
  private static synchronized void clearCurrent(String where) {
    current.put(where,null);
  }

  /**
   * Create a new rendezvous with the given identifier
   * @param where The rendezvous identifier
   */
  private Rendezvous(String where, int expected) {
    this.where = where;
    this.expected = expected;
  }

  /** The rendezvous identifier */
  private final String where;

  /** The rank that was given to the last thread to arrive at the rendezvous */
  private int currentRank = 0;

  /** The expected number of threads */
  private final int expected;

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   * @param expected TODO
   */
  private synchronized int rendezvous() {
    int rank = ++currentRank;
    if (currentRank == expected) {
      /* This is no longer the current barrier */
      clearCurrent(where);
      notifyAll();
    } else {
      while (currentRank != expected) {
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
   * Dispatch to the current collector rendezvous object
   * @param where
   * @return
   */
  static int rendezvous(String where) {
    return current(where,VM.activePlan.collectorCount()).rendezvous();
  }

  /**
   * Dispatch to the named mutator rendezvous object
   * @param where
   * @return
   */
  static int rendezvous(String where, int expected) {
    return current("Barrier-"+where,expected).rendezvous();
  }
}
