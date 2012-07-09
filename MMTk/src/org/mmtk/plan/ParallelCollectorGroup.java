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
package org.mmtk.plan;

import org.mmtk.utility.Constants;

import org.mmtk.vm.Monitor;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class represents a pool of collector contexts that can be triggered
 * to perform collection activity.
 */
@Uninterruptible
public class ParallelCollectorGroup implements Constants {

  /****************************************************************************
   * Instance fields
   */

  /** The name of this collector context group. */
  private final String name;

  /** The collector context instances operating within this group */
  private ParallelCollector[] contexts;

  /** Lock used to manage group state. */
  private Monitor lock;

  /** The number of cycles triggered */
  private volatile int triggerCount;

  /** The number of threads that are currently parked */
  private volatile int contextsParked;

  /** Is there an abort request outstanding? */
  private volatile boolean aborted;

  /** Used to count threads during calls to rendezvous() */
  private int[] rendezvousCounter = new int[2];

  /** Which rendezvous counter is currently in use */
  private volatile int currentRendezvousCounter;

  /****************************************************************************
   *
   * Initialization
   */
  public ParallelCollectorGroup(String name) {
    this.name = name;
  }

  /**
   * @return The number of active collector contexts.
   */
  public int activeWorkerCount() {
    return contexts.length;
  }

  /**
   * Initialize the collector context group.
   *
   * @param size The number of collector contexts within the group.
   * @param klass The type of collector context to create.
   */
  @Interruptible
  public void initGroup(int size, Class<? extends ParallelCollector> klass) {
    this.lock = VM.newHeavyCondLock("CollectorContextGroup");
    this.triggerCount = 1;
    this.contexts = new ParallelCollector[size];
    for(int i = 0; i < size; i++) {
      try {
        contexts[i] = klass.newInstance();
        contexts[i].group = this;
        contexts[i].workerOrdinal = i;
        VM.collection.spawnCollectorContext(contexts[i]);
      } catch (Throwable t) {
        VM.assertions.fail("Error creating collector context '" + klass.getName() + "' for group '" + name + "': " + t.toString());
      }
    }
  }

  /**
   * Wake up the parked threads in this group.
   */
  public void triggerCycle() {
    lock.lock();
    triggerCount++;
    contextsParked = 0;
    lock.broadcast();
    lock.unlock();
  }

  /**
   * Signal that you would like the threads to park abruptly. Has no effect if no cycle is active.
   */
  public void abortCycle() {
    lock.lock();
    if (contextsParked < contexts.length) {
      aborted = true;
    }
    lock.unlock();
  }

  /**
   * Has the cycle been aborted?
   */
  public boolean isAborted() {
    return aborted;
  }

  /**
   * Wait until the group is idle.
   */
  public void waitForCycle() {
    lock.lock();
    while (contextsParked < contexts.length) {
      lock.await();
    }
    lock.unlock();
  }

  /**
   * Park the given collector in the group. The given context must be a member of this group.
   *
   * @param context The context to park.
   */
  public void park(ParallelCollector context) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isMember(context));
    lock.lock();
    context.lastTriggerCount++;
    if (context.lastTriggerCount == triggerCount) {
      contextsParked++;
      if (contextsParked == contexts.length) {
        aborted = false;
      }
      lock.broadcast();
      while (context.lastTriggerCount == triggerCount) {
        lock.await();
      }
    }
    lock.unlock();
  }

  /**
   * Is the given context and member of this group.
   *
   * @param context The context to pass.
   * @return {@code true} if the context is a member.
   */
  public boolean isMember(CollectorContext context) {
    for(CollectorContext c: contexts) {
      if (c == context) {
        return true;
      }
    }
    return false;
  }

  /**
   * Rendezvous with other active threads in this group.
   *
   * @return The order in which you entered the rendezvous.
   */
  public int rendezvous() {
    lock.lock();
    int i = currentRendezvousCounter;
    int me = rendezvousCounter[i]++;
    if (me == contexts.length-1) {
      currentRendezvousCounter ^= 1;
      rendezvousCounter[currentRendezvousCounter] = 0;
      lock.broadcast();
    } else {
      while(rendezvousCounter[i] < contexts.length) {
        lock.await();
      }
    }
    lock.unlock();
    return me;
  }
}
