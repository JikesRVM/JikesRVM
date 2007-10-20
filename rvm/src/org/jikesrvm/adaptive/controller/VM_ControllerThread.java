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
package org.jikesrvm.adaptive.controller;

import java.util.Enumeration;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.OSR_OnStackReplacementEvent;
import org.jikesrvm.adaptive.OSR_OrganizerThread;
import org.jikesrvm.adaptive.database.methodsamples.VM_MethodCountData;
import org.jikesrvm.adaptive.measurements.listeners.VM_EdgeListener;
import org.jikesrvm.adaptive.measurements.listeners.VM_YieldCounterListener;
import org.jikesrvm.adaptive.measurements.organizers.VM_AccumulatingMethodSampleOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.VM_DecayOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.VM_DynamicCallGraphOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.VM_MethodSampleOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.VM_Organizer;
import org.jikesrvm.adaptive.recompilation.VM_CompilationThread;
import org.jikesrvm.adaptive.recompilation.VM_CompilerDNA;
import org.jikesrvm.adaptive.recompilation.VM_InvocationCounts;
import org.jikesrvm.adaptive.util.VM_AOSGenerator;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.adaptive.util.VM_AOSOptions;
import org.jikesrvm.scheduler.VM_Scheduler.ThreadModel;

/**
 * This class implements the controller thread.  This entity is the brains of
 * the adaptive optimization system.  It communicates with the runtime
 * measurements subsystem to instruct and gather profiling information.
 * It also talks to the compilation threads to generate
 *     a) instrumented executables;
 *     b) optimized executables;
 *     c) static information about a method; or
 *     d) all of the above.
 */
public final class VM_ControllerThread extends ThreadModel {

  /**
   * constructor
   * @param sentinel   An object to signal when up and running
   */
  VM_ControllerThread(Object sentinel) {
    super("VM_ControllerThread");
    this.sentinel = sentinel;
    makeDaemon(true);
  }

  private final Object sentinel;

  /**
   * There are several ways in which a dcg organizer might
   * be created; keep track of it once it is created so that
   * we only create one instance of it.
   */
  private VM_DynamicCallGraphOrganizer dcgOrg;

  /**
   * This method is the entry point to the controller, it is called when
   * the controllerThread is created.
   */
  public void run() {
    // save this object so others can access it, if needed
    VM_Controller.controllerThread = this;

    // Bring up the logging system
    VM_AOSLogging.boot();
    if (VM_Controller.options.ENABLE_ADVICE_GENERATION) {
      VM_AOSGenerator.boot();
    }
    VM_AOSLogging.controllerStarted();

    // Create measurement entities that are NOT related to
    // adaptive recompilation
    createProfilers();

    if (!VM_Controller.options.ENABLE_RECOMPILATION) {
      // We're running an AOS bootimage with a non-adaptive primary strategy.
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      controllerInitDone();
      VM.sysWriteln("AOS: In non-adaptive mode; controller thread exiting.");
      return; // controller thread exits.
    }

    if ((VM_Controller.options.ENABLE_REPLAY_COMPILE) || (VM_Controller.options.ENABLE_PRECOMPILE)) {
      // if we want to do precompile, we need to initial optimization plans
      // just allow the advice to be the max opt level 2
      VM_Controller.options.DERIVED_MAX_OPT_LEVEL = 2;
      if (VM_Controller.options.sampling()) {
        // Create our set of standard optimization plans.
        VM_Controller.recompilationStrategy.init();
      } else if (VM_Controller.options.counters()) {
        VM_InvocationCounts.init();
      }
      VM_Controller.osrOrganizer = new OSR_OrganizerThread();
      VM_Controller.osrOrganizer.start();
      createCompilationThread();
      // We're running an AOS bootimage with a non-adaptive primary strategy.
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      controllerInitDone();
      // to have a fair comparison, we need to create the data structures
      // of organizers
      createOrganizerThreads();
      VM.sysWriteln("AOS: In replay mode; controller thread only runs for OSR inlining.");
      while (true) {
        if (VM_Controller.options.EARLY_EXIT && VM_Controller.options.EARLY_EXIT_TIME < VM_Controller.controllerClock) {
          VM_Controller.stop();
        }
        Object event = VM_Controller.controllerInputQueue.deleteMin();
        ((OSR_OnStackReplacementEvent) event).process();
      }

    }

    // Initialize the CompilerDNA class
    // This computes some internal options, must be done early in boot process
    VM_CompilerDNA.init();

    // Create the organizerThreads and schedule them
    createOrganizerThreads();

    // Create the compilationThread and schedule it
    createCompilationThread();

    if (VM_Controller.options.sampling()) {
      // Create our set of standard optimization plans.
      VM_Controller.recompilationStrategy.init();
    } else if (VM_Controller.options.counters()) {
      VM_InvocationCounts.init();

    }

    controllerInitDone();

    // Enter main controller loop.
    // Pull an event to process off of
    // VM_Controller.controllerInputQueue and handle it.
    // If no events are on the queue, then the deleteMin call will
    // block until an event is available.
    // Repeat forever.
    while (true) {
      if (VM_Controller.options.EARLY_EXIT && VM_Controller.options.EARLY_EXIT_TIME < VM_Controller.controllerClock) {
        VM_Controller.stop();
      }
      Object event = VM_Controller.controllerInputQueue.deleteMin();
      ((VM_ControllerInputEvent) event).process();
    }
  }

  // Now that we're done initializing, Schedule all the organizer threads
  // and signal the sentinel object.
  private void controllerInitDone() {
    for (Enumeration<VM_Organizer> e = VM_Controller.organizers.elements(); e.hasMoreElements();) {
      VM_Organizer o = e.nextElement();
      o.start();
    }

    try {
      synchronized (sentinel) {
        sentinel.notify();
      }
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to start up controller subsystem");
    }
  }

  /**
   * Called when the controller thread is about to wait on
   * VM_Controller.controllerInputQueue
   */
  public void aboutToWait() {
  }

  /**
   * Called when the controller thread is woken after waiting on
   * VM_Controller.controllerInputQueue
   */
  public void doneWaiting() {
    VM_ControllerMemory.incrementNumAwoken();
  }

  ///////////////////////
  // Initialization.
  //  Create AOS threads.
  //  Initialize AOS data structures that depend on command line arguments.
  ///////////////////////

  /**
   *  Create the compilationThread and schedule it
   */
  private void createCompilationThread() {
    VM_CompilationThread ct = new VM_CompilationThread();
    VM_Controller.compilationThread = ct;
    ct.start();
  }

  /**
   * Create a dynamic call graph organizer of one doesn't already exist
   */
  private void createDynamicCallGraphOrganizer() {
    if (dcgOrg == null) {
      dcgOrg = new VM_DynamicCallGraphOrganizer(new VM_EdgeListener());
      VM_Controller.organizers.addElement(dcgOrg);
    }
  }

  /**
   * Create profiling entities that are independent of whether or not
   * adaptive recompilation is actually enabled.
   */
  private void createProfilers() {
    VM_AOSOptions opts = VM_Controller.options;

    if (opts.GATHER_PROFILE_DATA) {
      VM_Controller.organizers.addElement(new VM_AccumulatingMethodSampleOrganizer());

      createDynamicCallGraphOrganizer();
    }
  }

  /**
   *  Create the organizerThreads and schedule them
   */
  private void createOrganizerThreads() {
    VM_AOSOptions opts = VM_Controller.options;

    if (opts.sampling()) {
      // Primary backing store for method sample data
      VM_Controller.methodSamples = new VM_MethodCountData();

      // Install organizer to drive method recompilation
      VM_Controller.organizers.addElement(new VM_MethodSampleOrganizer(opts.DERIVED_FILTER_OPT_LEVEL));
      // Additional set up for feedback directed inlining
      if (opts.ADAPTIVE_INLINING) {
        VM_Organizer decayOrganizer = new VM_DecayOrganizer(new VM_YieldCounterListener(opts.DECAY_FREQUENCY));
        VM_Controller.organizers.addElement(decayOrganizer);
        createDynamicCallGraphOrganizer();
      }
    }

    if ((!VM_Controller.options.ENABLE_REPLAY_COMPILE) && (!VM_Controller.options.ENABLE_PRECOMPILE)) {
      VM_Controller.osrOrganizer = new OSR_OrganizerThread();
      VM_Controller.osrOrganizer.start();
    }
  }

  /**
   * Final report
   */
  public static void report() {
    VM_AOSLogging.controllerCompleted();
  }

}
