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
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * A garbage collection proceeds as a sequence of phases. Each
 * phase is either simple (singular) or complex (an array).
 *
 * The context an individual phase executes in may be global, mutator,
 * or collector.
 *
 * Phases are executed within a stack and all synchronization between
 * parallel GC threads is managed from within this class.
 *
 * @see CollectorContext#collectionPhase
 * @see MutatorContext#collectionPhase
 * @see Plan#collectionPhase
 */
@Uninterruptible
public abstract class Phase implements Constants {
  /***********************************************************************
  *
  * Phase allocation and storage.
  */

  /** The maximum number of phases */
  private static final int MAX_PHASES = 64;
  /** The array of phase instances. Zero is unused. */
  private static final Phase[] phases = new Phase[MAX_PHASES];
  /** The id to be allocated for the next phase */
  private static short nextPhaseId = 1;

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

  /** Get the phase id component of an encoded phase */
  protected static short getPhaseId(int scheduledPhase) {
    short phaseId = (short)(scheduledPhase & 0x0000FFFF);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(phaseId > 0);
    return phaseId;
  }

  /**
   * @param phaseId The unique phase identifier.
   * @return The name of the phase.
   */
  public static String getName(short phaseId) {
    return phases[phaseId].name;
  }

  /** Get the ordering component of an encoded phase */
  protected static short getSchedule(int scheduledPhase) {
    short ordering = (short)((scheduledPhase >> 16) & 0x0000FFFF);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(ordering > 0);
    return ordering;
  }

  /** Get the ordering component of an encoded phase */
  protected static String getScheduleName(short ordering) {
    switch (ordering) {
      case SCHEDULE_GLOBAL:      return "Global";
      case SCHEDULE_COLLECTOR:   return "Collector";
      case SCHEDULE_MUTATOR:     return "Mutator";
      case SCHEDULE_PLACEHOLDER: return "Placeholder";
      case SCHEDULE_COMPLEX:     return "Complex";
      default:                   return "UNKNOWN!";
    }
  }

  /**
   * Construct a phase.
   *
   * @param name Display name of the phase
   */
  @Interruptible
  public static short createSimple(String name) {
    return new SimplePhase(name).getId();
  }

  /**
   * Construct a phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   */
  @Interruptible
  public static short createSimple(String name, Timer timer) {
    return new SimplePhase(name, timer).getId();
  }

  /**
   * Construct a complex phase.
   *
   * @param name Display name of the phase
   * @param scheduledPhases The phases in this complex phase.
   */
  @Interruptible
  public static short createComplex(String name,int... scheduledPhases) {
    return new ComplexPhase(name, scheduledPhases).getId();
  }

  /**
   * Construct a complex phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   * @param scheduledPhases The phases in this complex phase.
   */
  @Interruptible
  public static short createComplex(String name, Timer timer, int... scheduledPhases) {
    return new ComplexPhase(name, timer, scheduledPhases).getId();
  }

  /**
   * Take the passed phase and return an encoded phase to
   * run that phase as a complex phase.
   *
   * @param phaseId The phase to run as complex
   * @return The encoded phase value.
   */
  public static int scheduleComplex(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Phase.getPhase(phaseId) instanceof ComplexPhase);
    return (SCHEDULE_COMPLEX << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to
   * run that phase in a global context;
   *
   * @param phaseId The phase to run globally
   * @return The encoded phase value.
   */
  public static int scheduleGlobal(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Phase.getPhase(phaseId) instanceof SimplePhase);
    return (SCHEDULE_GLOBAL << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to
   * run that phase in a collector context;
   *
   * @param phaseId The phase to run on collectors
   * @return The encoded phase value.
   */
  public static int scheduleCollector(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Phase.getPhase(phaseId) instanceof SimplePhase);
    return (SCHEDULE_COLLECTOR << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to
   * run that phase in a mutator context;
   *
   * @param phaseId The phase to run on mutators
   * @return The encoded phase value.
   */
  public static int scheduleMutator(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Phase.getPhase(phaseId) instanceof SimplePhase);
    return (SCHEDULE_MUTATOR << 16) + phaseId;
  }

  /**
   * Take the passed phase and return an encoded phase to
   * run that phase in a mutator context;
   *
   * @param phaseId The phase to run on mutators
   * @return The encoded phase value.
   */
  public static int schedulePlaceholder(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Phase.getPhase(phaseId) instanceof SimplePhase);
    return (SCHEDULE_PLACEHOLDER << 16) + phaseId;
  }

  /***********************************************************************
   *
   * Phase instance fields/methods.
   */

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

  /***********************************************************************
   *
   * Phase stack
   */

  /** The maximum stack depth for the phase stack. */
  private static final int MAX_PHASE_STACK_DEPTH = MAX_PHASES;

  /** Stores the current sub phase for a complex phase. Each entry corresponds to a phase stack entry */
  private static int[] complexPhaseCursor = new int[MAX_PHASE_STACK_DEPTH];

  /** The phase stack. Stores the current nesting of phases */
  private static int[] phaseStack = new int[MAX_PHASE_STACK_DEPTH];

  /** The current stack pointer */
  private static int phaseStackPointer = -1;

  /**
   * The current even (0 mod 2) scheduled phase.
   * As we only sync at the end of a phase we need this to ensure that
   * the primary thread setting the phase does not race with the other
   * threads reading it.
   */
  private static int evenScheduledPhase;

  /**
   * The current odd (1 mod 2) scheduled phase.
   * As we only sync at the end of a phase we need this to ensure that
   * the primary thread setting the phase does not race with the other
   * threads reading it.
   */
  private static int oddScheduledPhase;

  /**
   * Do we need to add a sync point to reset the mutator count. This
   * is necessary for consecutive mutator phases and unneccessary
   * otherwise. Again we separate in even and odd to ensure that there
   * is no race between the primary thread setting and the helper
   * threads reading.
   */
  private static boolean evenMutatorResetRendezvous;

  /**
   * Do we need to add a sync point to reset the mutator count. This
   * is necessary for consecutive mutator phases and unneccessary
   * otherwise. Again we separate in even and odd to ensure that there
   * is no race between the primary thread setting and the helper
   * threads reading.
   */
  private static boolean oddMutatorResetRendezvous;

  /**
   * The complex phase whose timer should be started after the next
   * rendezvous. We can not start the timer at the point we determine
   * the next complex phase as we determine the next phase at the
   * end of the previous phase before the sync point.
   */
  private static short startComplexTimer;

  /**
   * The complex phase whose timer should be stopped after the next
   * rendezvous. We can not start the timer at the point we determine
   * the next complex phase as we determine the next phase at the
   * end of the previous phase before the sync point.
   */
  private static short stopComplexTimer;

  /**
   * Place a phase on the phase stack and begin processing.
   *
   * @param scheduledPhase The phase to execute
   * @return True if the phase stack is exhausted.
   */
  public static boolean beginNewPhaseStack(int scheduledPhase) {
    int order = VM.collection.rendezvous(1001);

    if (order == 1) {
      pushScheduledPhase(scheduledPhase);
    }
    return processPhaseStack(false);
  }

  /**
   * Process the phase stack. This method is called by multiple threads.
   */
  private static boolean processPhaseStack(boolean resume) {
    int order = VM.collection.rendezvous(1001);
    final boolean primary = order == 1;

    boolean log = Options.verbose.getValue() >= 6;
    boolean logDetails = Options.verbose.getValue() >= 7;

    if (primary && resume) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Phase.isPhaseStackEmpty());
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Plan.gcInProgress());
      Plan.setGCStatus(Plan.GC_PROPER);
    }

    /* In order to reduce the need for synchronization, we keep an odd or even
     * counter for the number of phases processed. As each phase has a single
     * rendezvous it is only possible to be out by one so the odd or even counter
     * protects us. */
    boolean isEvenPhase = true;

    if (primary) {
      /* First phase will be even, so we say we are odd here so that the next phase set is even*/
      setNextPhase(false, getNextPhase(), false);
    }

    /* Make sure everyone sees the first phase */
    VM.collection.rendezvous(1002);

    /* Global and Collector instances used in phases */
    Plan plan = VM.activePlan.global();
    CollectorContext collector = VM.activePlan.collector();

    /* The main phase execution loop */
    int scheduledPhase;
    while((scheduledPhase = getCurrentPhase(isEvenPhase)) > 0) {
      short schedule = getSchedule(scheduledPhase);
      short phaseId = getPhaseId(scheduledPhase);
      Phase p = getPhase(phaseId);

      /* Start the timer(s) */
      if (primary) {
        if (resume) {
          resumeComplexTimers();
        }
        if (p.timer != null) p.timer.start();
        if (startComplexTimer > 0) {
          Phase.getPhase(startComplexTimer).timer.start();
          startComplexTimer = 0;
        }
      }

      if (log) {
        Log.write("Execute ");
        p.logPhase();
      }

      /* Execute a single simple scheduled phase */
      switch (schedule) {
        /* Global phase */
        case SCHEDULE_GLOBAL: {
          if (logDetails) Log.writeln(" as Global...");
          if (primary) {
            if (VM.DEBUG) VM.debugging.globalPhase(phaseId,true);
            plan.collectionPhase(phaseId);
            if (VM.DEBUG) VM.debugging.globalPhase(phaseId,false);
          }
          break;
        }

        /* Collector phase */
        case SCHEDULE_COLLECTOR: {
          if (logDetails) Log.writeln(" as Collector...");
          if (VM.DEBUG) VM.debugging.collectorPhase(phaseId,order,true);
          collector.collectionPhase(phaseId, primary);
          if (VM.DEBUG) VM.debugging.collectorPhase(phaseId,order,false);
          break;
        }

        /* Mutator phase */
        case SCHEDULE_MUTATOR: {
          if (logDetails) Log.writeln(" as Mutator...");
          /* Iterate through all mutator contexts */
          MutatorContext mutator;
          while ((mutator = VM.activePlan.getNextMutator()) != null) {
            if (VM.DEBUG) VM.debugging.mutatorPhase(phaseId,mutator.getId(),true);
            mutator.collectionPhase(phaseId, primary);
            if (VM.DEBUG) VM.debugging.mutatorPhase(phaseId,mutator.getId(),false);
          }
          break;
        }

        default: {
          /* getNextPhase has done the wrong thing */
          VM.assertions.fail("Invalid schedule in Phase.processPhaseStack");
          break;
        }
      }

      if (primary) {
        /* Set the next phase by processing the stack */
        int next = getNextPhase();
        boolean needsResetRendezvous = (next > 0) && (schedule == SCHEDULE_MUTATOR && getSchedule(next) == SCHEDULE_MUTATOR);
        setNextPhase(isEvenPhase, next, needsResetRendezvous);
      }

      /* Sync point after execution of a phase */
      VM.collection.rendezvous(1004);

      /* Mutator phase reset */
      if (primary && schedule == SCHEDULE_MUTATOR) {
        VM.activePlan.resetMutatorIterator();
      }

      /* At this point, in the case of consecutive phases with mutator
       * scheduling, we have to double-synchronize to ensure all
       * collector threads see the reset mutator counter. */
      if (needsMutatorResetRendezvous(isEvenPhase)) {
        VM.collection.rendezvous(1005);
      }

      /* Stop the timer(s) */
      if (primary) {
        if (p.timer != null) p.timer.stop();
        if (stopComplexTimer > 0) {
          Phase.getPhase(stopComplexTimer).timer.stop();
          stopComplexTimer = 0;
        }
      }

      /* Flip the even / odd phase sense */
      isEvenPhase = !isEvenPhase;
      resume = false;
    }

    /* Phase stack exhausted so we return true */
    return true;
  }

  /**
   * Get the next phase.
   */
  private static int getCurrentPhase(boolean isEvenPhase) {
    return isEvenPhase ? evenScheduledPhase : oddScheduledPhase;
  }

  /**
   * Do we need a mutator reset rendezvous in this phase?
   */
  private static boolean needsMutatorResetRendezvous(boolean isEvenPhase) {
    return isEvenPhase ? evenMutatorResetRendezvous : oddMutatorResetRendezvous;
  }
  /**
   * Set the next phase. If we are in an even phase the next phase is odd.
   */
  private static void setNextPhase(boolean isEvenPhase, int scheduledPhase, boolean needsResetRendezvous) {
    if (isEvenPhase) {
      oddScheduledPhase = scheduledPhase;
      evenMutatorResetRendezvous = needsResetRendezvous;
    } else {
      evenScheduledPhase = scheduledPhase;
      oddMutatorResetRendezvous = needsResetRendezvous;
    }
  }

  /**
   * Pull the next scheduled phase off the stack. This may involve
   * processing several complex phases and skipping placeholders, etc.
   *
   * @return The next phase to run, or -1 if no phases are left.
   */
  private static int getNextPhase() {
    boolean allowConcurrentPhase = Plan.collectionTrigger == Collection.INTERNAL_PHASE_GC_TRIGGER;

    while (phaseStackPointer >= 0) {
      int scheduledPhase = peekScheduledPhase();
      short schedule = getSchedule(scheduledPhase);
      short phaseId = getPhaseId(scheduledPhase);

      switch(schedule) {
        case SCHEDULE_PLACEHOLDER: {
          /* Placeholders are ignored and we continue looking */
          popScheduledPhase();
          continue;
        }

        case SCHEDULE_GLOBAL:
        case SCHEDULE_COLLECTOR:
        case SCHEDULE_MUTATOR: {
          /* Simple phases are just popped off the stack and executed */
          popScheduledPhase();
          return scheduledPhase;
        }

        case SCHEDULE_COMPLEX: {
          /* A complex phase may either be a newly pushed complex phase,
           * or a complex phase we are in the process of executing in
           * which case we move to the next subphase. */
          ComplexPhase p = (ComplexPhase)getPhase(phaseId);
          int cursor = incrementComplexPhaseCursor();
          if (cursor == 0 && p.timer != null) {
            /* Tell the primary thread to start the timer after the next sync. */
            startComplexTimer = phaseId;
          }
          if (cursor < p.count()) {
            /* There are more entries, we push the next one and continue */
            pushScheduledPhase(p.get(cursor));
            continue;
          }

          /* We have finished this complex phase */
          popScheduledPhase();
          if (p.timer != null) {
            /* Tell the primary thread to stop the timer after the next sync. */
            stopComplexTimer = phaseId;
          }
          continue;
        }

        default: {
          VM.assertions.fail("Invalid phase type encountered");
        }
      }
    }
    return -1;
  }

  /**
   * Pause all of the timers for the complex phases sitting in the stack.
   */
  private static void pauseComplexTimers() {
    for(int i=phaseStackPointer; i >=0; i--) {
      Phase p = getPhase(getPhaseId(phaseStack[i]));
      if (p.timer != null) p.timer.stop();
    }
  }

  /**
   * Resume all of the timers for the complex phases sitting in the stack.
   */
  private static void resumeComplexTimers() {
    for(int i=phaseStackPointer; i >=0; i--) {
      Phase p = getPhase(getPhaseId(phaseStack[i]));
      if (p.timer != null) p.timer.start();
    }
  }

  /**
   * Return true if phase stack is empty, false otherwise.
   *
   * @return true if phase stack is empty, false otherwise.
   */
  @Inline
  public static boolean isPhaseStackEmpty() {
    return phaseStackPointer < 0;
  }

  /**
   * Clears the scheduled phase stack.
   */
  @Inline
  public static void resetPhaseStack() {
    phaseStackPointer = -1;
  }

  /**
   * Push a scheduled phase onto the top of the work stack.
   *
   * @param scheduledPhase The scheduled phase.
   */
  @Inline
  public static void pushScheduledPhase(int scheduledPhase) {
    phaseStack[++phaseStackPointer] = scheduledPhase;
    complexPhaseCursor[phaseStackPointer] = 0;
  }

  /**
   * Increment the cursor associated with the current phase
   * stack entry. This is used to remember the current sub phase
   * when executing a complex phase.
   *
   * @return The old value of the cursor.
   */
  @Inline
  private static int incrementComplexPhaseCursor() {
    return complexPhaseCursor[phaseStackPointer]++;
  }

  /**
   * Pop off the scheduled phase at the top of the work stack.
   */
  @Inline
  private static int popScheduledPhase() {
    return phaseStack[phaseStackPointer--];
  }

  /**
   * Peek the scheduled phase at the top of the work stack.
   */
  @Inline
  private static int peekScheduledPhase() {
    return phaseStack[phaseStackPointer];
  }
}
