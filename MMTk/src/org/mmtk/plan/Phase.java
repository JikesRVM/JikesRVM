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
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * A garbage collection proceeds as a sequence of phases. Each
 * phase is either simple (singular) or complex (an array).
 *
 * The context an individual phase executes in may be global, mutator,
 * or collector.
 *
 * @see CollectorContext#collectionPhase
 * @see MutatorContext#collectionPhase
 * @see Plan#collectionPhase
 *
 * Urgent TODO: Assess cost of rendezvous when running in parallel.
 * It should be possible to remove some by thinking about phases more
 * carefully
 */
@Uninterruptible
public abstract class Phase implements Constants {
  private static final int MAX_PHASES = 64;
  private static final Phase[] phases = new Phase[MAX_PHASES];
  private static short nextPhaseId = 1;

  /** Get the ordering component of an encoded phase */
  static short getSchedule(int scheduledPhase) {
    short ordering = (short)((scheduledPhase >> 16) & 0x0000FFFF);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ordering > 0);
    return ordering;
  }
  
  /** Get the ordering component of an encoded phase */
  static String getScheduleName(short ordering) {
    switch (ordering) {
      case SCHEDULE_GLOBAL:      return "Global";
      case SCHEDULE_COLLECTOR:   return "Collector";
      case SCHEDULE_MUTATOR:     return "Mutator";
      case SCHEDULE_PLACEHOLDER: return "Placeholder";
      case SCHEDULE_COMPLEX:     return "Complex";
      default:                   return "UNKNOWN!";
    }
  }

  /** Get the phase id component of an encoded phase */
  static short getPhaseId(int scheduledPhase) {
    short phaseId = (short)(scheduledPhase & 0x0000FFFF);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId > 0);
    return phaseId;
  }

  /** Run the phase globally. */
  protected static final short SCHEDULE_GLOBAL = 1;

  /** Run the phase on collectors. */
  protected static final short SCHEDULE_COLLECTOR = 2;

  /** Run the phase on mutators. */
  protected static final short SCHEDULE_MUTATOR = 3;
  
  /** Don't run this phase. */
  protected static final short SCHEDULE_PLACEHOLDER = 100;
  
  /** This is a complex phase. */
  protected static final short SCHEDULE_COMPLEX = 101;

  /**
   * Construct a phase.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   */
  @Interruptible
  public static final short createSimple(String name) {
    return new SimplePhase(name).getId();
  }

  /**
   * Construct a phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   */
  @Interruptible
  public static final short createSimple(String name, Timer timer) {
    return new SimplePhase(name, timer).getId();
  }

  /**
   * Construct a complex phase.
   *
   * @param name Display name of the phase
   * @param scheduledPhases The phases in this complex phase.
   */
  @Interruptible
  public static final short createComplex(String name,int... scheduledPhases) {
    short complexPhaseId = new ComplexPhase(name, scheduledPhases).getId(); 
    return complexPhaseId;
  }

  /**
   * Construct a complex phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   * @param scheduledPhases The phases in this complex phase.
   */
  @Interruptible
  public static final short createComplex(String name, Timer timer, int... scheduledPhases) {
    short complexPhaseId = new ComplexPhase(name, timer, scheduledPhases).getId(); 
    return complexPhaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to 
   * run that phase as a complex phase.
   * 
   * @param phaseId The phase to run as complex
   * @return The encoded phase value.
   */
  public static final int scheduleComplex(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId < MAX_PHASES);
    return (SCHEDULE_COMPLEX << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to 
   * run that phase in a global context;
   * 
   * @param phaseId The phase to run globally
   * @return The encoded phase value.
   */
  public static final int scheduleGlobal(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId < MAX_PHASES);
    return (SCHEDULE_GLOBAL << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to 
   * run that phase in a collector context;
   * 
   * @param phaseId The phase to run on collectors
   * @return The encoded phase value.
   */
  public static final int scheduleCollector(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId < MAX_PHASES);
    return (SCHEDULE_COLLECTOR << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to 
   * run that phase in a mutator context;
   * 
   * @param phaseId The phase to run on mutators
   * @return The encoded phase value.
   */
  public static final int scheduleMutator(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId < MAX_PHASES);
    return (SCHEDULE_MUTATOR << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to 
   * run that phase in a mutator context;
   * 
   * @param phaseId The phase to run on mutators
   * @return The encoded phase value.
   */
  public static final int schedulePlaceholder(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId < MAX_PHASES);
    return (SCHEDULE_PLACEHOLDER << 16) + phaseId;
  }

  /**
   * Replace a scheduled phase. Used for example to replace a placeholder.
   *
   * @param rootPhase Root phase id.
   * @param oldScheduledPhase The scheduled phase to replace.
   * @param newScheduledPhase The new scheduled phase.
   */
  public static void replacePhase(int rootPhase, int oldScheduledPhase, int newScheduledPhase) {
    
  }

  /**
   * The unique phase identifier.
   */
  protected final short id;

  /**
   * The name of the phase.
   */
  protected final String name;

  /**
   * The Timer that is started and stopped around the execution of this
   * phase.
   */
  protected final Timer timer;

  /**
   * Create a new Phase. This involves creating a corresponding Timer
   * instance, allocating a unique identifier, and registering the
   * Phase.
   *
   * @param name The name for the phase.
   */
  protected Phase(String name) {
    this(name, new Timer(name, false, true));
  }

  /**
   * Create a new phase. This involves setting the corresponding Timer
   * instance, allocating a unique identifier, and registering the Phase.
   *
   * @param name The name of the phase.
   * @param timer The timer, or null if this is an untimed phase.
   */
  protected Phase(String name, Timer timer) {
    this.name = name;
    this.timer = timer;
    this.id = nextPhaseId++;
    phases[this.id] = this;
  }

  /**
   * @return The unique identifier for this phase.
   */
  public final short getId() {
    return this.id;
  }

  /**
   * Display a phase for debugging purposes.
   */
  protected abstract void logPhase();
  
  /**
   * @param phaseId The unique phase identifier.
   * @return The name of the phase.
   */
  public static String getName(short phaseId) {
    return phases[phaseId].name;
  }

  /**
   * Execute the specified phase, synchronizing initially.
   */
  public static void executeScheduledPhase(int scheduledPhase) {
    short phaseId = getPhaseId(scheduledPhase);
    short schedule = getSchedule(scheduledPhase);
    boolean primary = rendezvous(0, phaseId, schedule);
    Phase p = getPhase(phaseId);
    p.execute(primary, schedule);
  }

  /**
   * Execute a phase according to the supplied schedule
   *
   * @param primary Should global actions be performed on this thread.
   * @param schedule The schedule to use during execution
   */
  protected abstract void execute(boolean primary, short schedule);

  /**
   * Call rendezvous.
   * 
   * @param where A source location identifier.
   * @param schedule The schedule used.
   * @return True if the first to the rendezvous.
   */
  @Inline
  protected final boolean rendezvous(int where, short schedule) {
    return rendezvous(where, id, schedule);  
  }

  /**
   * Call rendezvous.
   * 
   * @param where A source location identifier.
   * @param schedule The schedule used.
   * @return True if the first to the rendezvous.
   */
  @Inline
  protected static boolean rendezvous(int where, short id, short schedule) {
    return VM.collection.rendezvous(100000 * schedule + 1000 * id + where) == 1;  
  }

  /**
   * Retrieve a phase by the unique phase identifier.
   *
   * @param id The phase identifier.
   * @return The Phase instance.
   */
  public static Phase getPhase(short id) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(id < nextPhaseId, "Phase ID unknown");
      VM.assertions._assert(phases[id] != null, "Uninitialised phase");
    }
    return phases[id];
  }
}
