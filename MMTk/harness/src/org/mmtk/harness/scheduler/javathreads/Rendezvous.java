/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.scheduler.javathreads;

class Rendezvous {
  /** The rank that was given to the last thread to arrive at the rendezvous */
  private int currentRank = 0;

  private int currentPlace = -1;

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  int rendezvous(int where) {
    synchronized(this) {
      int rank = ++currentRank;
      if (rank == 1) {
        currentPlace = where;
      } else {
        assert where == currentPlace : "Mismatched rendezvous";
      }
      if (currentRank == org.mmtk.vm.VM.activePlan.collectorCount()) {
        currentRank = 0;
        notifyAll();
      } else {
        try {
          wait();
        } catch (InterruptedException ie) {
          assert false : "Interrupted in rendezvous";
        }
      }
      return rank;
    }
  }
}
