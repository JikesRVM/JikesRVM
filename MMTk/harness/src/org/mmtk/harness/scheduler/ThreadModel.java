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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.CollectorContext;
import org.mmtk.utility.Log;
import org.mmtk.vm.Monitor;

/**
 * Abstract implementation of a threading model.
 */
public abstract class ThreadModel {

  /**
   * Distinguish between setup/initialization and proper running of the harness
   */
  private boolean running = false;

  /** The global state of the scheduler */
  public enum State {
    /** Mutator threads are running */
    MUTATOR,

    /** GC requested, GC will start once all mutators have yielded */
    BLOCKING,

    /** GC in progress */
    BLOCKED,

    /** Waiting on all GC threads to hibernate */
    RESUMING,
  }

  private static volatile State state = State.MUTATOR;

  protected void initCollectors() { }

  protected abstract void yield();

  protected abstract void scheduleMutator(Schedulable method);

  protected abstract void scheduleCollector(CollectorContext context);

  protected abstract Thread scheduleCollectorContext(CollectorContext item);

  protected abstract Log currentLog();

  protected abstract Mutator currentMutator();

  /* schedule GC */

  protected abstract boolean gcTriggered();

  protected abstract int mutatorRendezvous(String where, int expected);

  protected abstract CollectorContext currentCollector();

  protected abstract void waitForGC();

  protected abstract void schedule();

  protected abstract void scheduleGcThreads();

  /**
   * Resume all mutator threads after a stop-the-world phase has completed.
   */
  protected abstract void resumeAllMutators();


  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   */
  protected abstract void stopAllMutators();

  /**
   * An MMTk lock
   */
  protected abstract Lock newLock(String name);

  /**
   * An MMTk monitor
   */
  protected abstract Monitor newMonitor(String name);

  private static final Map<State,Set<State>> validTransitions = new EnumMap<State,Set<State>>(State.class);

  static {
    validTransitions.put(State.MUTATOR,EnumSet.of(State.BLOCKING));
    validTransitions.put(State.BLOCKING,EnumSet.of(State.BLOCKED));
    validTransitions.put(State.BLOCKED,EnumSet.of(State.MUTATOR));
    validTransitions.put(State.RESUMING,EnumSet.noneOf(State.class));
  }

  protected void setState(State state) {
    Trace.trace(Item.SCHEDULER,"State changing from %s to %s",ThreadModel.state,state);
    assert validTransitions.get(ThreadModel.state).contains(state) :
      "Illegal state transition, from "+ThreadModel.state+" to "+state;
    ThreadModel.state = state;
  }

  protected State getState() {
    return state;
  }

  protected boolean isState(State s) {
    return state == s;
  }

  protected boolean isRunning() {
    return running;
  }

  private void setRunning(boolean state) {
    running = state;
  }

  protected void startRunning() {
    setRunning(true);
  }

  protected void stopRunning() {
    setRunning(false);
  }

  public abstract boolean isMutator();

  public abstract boolean isCollector();
}
