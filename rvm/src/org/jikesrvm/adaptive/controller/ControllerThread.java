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
package org.jikesrvm.adaptive.controller;

import java.util.Enumeration;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.OnStackReplacementEvent;
import org.jikesrvm.adaptive.OSROrganizerThread;
import org.jikesrvm.adaptive.database.methodsamples.MethodCountData;
import org.jikesrvm.adaptive.measurements.listeners.EdgeListener;
import org.jikesrvm.adaptive.measurements.listeners.YieldCounterListener;
import org.jikesrvm.adaptive.measurements.organizers.AccumulatingMethodSampleOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.DecayOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.DynamicCallGraphOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.MethodSampleOrganizer;
import org.jikesrvm.adaptive.measurements.organizers.Organizer;
import org.jikesrvm.adaptive.recompilation.CompilationThread;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.adaptive.recompilation.InvocationCounts;
import org.jikesrvm.adaptive.util.AOSExternalOptions;
import org.jikesrvm.adaptive.util.AOSGenerator;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.adaptive.util.AOSOptions;
import org.jikesrvm.scheduler.SoftLatch;
import org.jikesrvm.scheduler.SystemThread;
import org.vmmagic.pragma.NonMoving;

/**
 * This class implements the controller thread.  This entity is the brains of
 * the adaptive optimization system.  It communicates with the runtime
 * measurements subsystem to instruct and gather profiling information.
 * It also talks to the compilation threads to generate
 * <ul>
 *   <li>instrumented executables;
 *   <li>optimized executables;
 *   <li>static information about a method; or
 *   <li>all of the above.
 * </ul>
 */
@NonMoving
public final class ControllerThread extends SystemThread {

  /**
   * constructor
   * @param sentinel   An object to signal when up and running
   */
  ControllerThread(SoftLatch sentinel) {
    super("ControllerThread");
    this.sentinel = sentinel;
  }

  private final SoftLatch sentinel;

  /**
   * There are several ways in which a dcg organizer might
   * be created; keep track of it once it is created so that
   * we only create one instance of it.
   */
  private DynamicCallGraphOrganizer dcgOrg;

  /**
   * This method is the entry point to the controller, it is called when
   * the controllerThread is created.
   */
  @Override
  public void run() {
    // save this object so others can access it, if needed
    Controller.controllerThread = this;

    // Cache option object because it'll be accessed often
    AOSExternalOptions opts = Controller.options;

    // Bring up the logging system
    AOSLogging.logger.boot();
    if (opts.ENABLE_ADVICE_GENERATION) {
      AOSGenerator.boot();
    }

    // Create measurement entities that are NOT related to
    // adaptive recompilation
    createProfilers();

    if (!opts.ENABLE_RECOMPILATION) {
      // We're running an AOS bootimage with a non-adaptive primary strategy.
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      if (opts.ENABLE_BULK_COMPILE || opts.ENABLE_PRECOMPILE) {
        Controller.options.DERIVED_MAX_OPT_LEVEL = 2;
        Controller.recompilationStrategy.init();
      }

      controllerInitDone();
      AOSLogging.logger.reportThatAOSIsInNonAdaptiveMode();
      return; // controller thread exits.
    }

    if (opts.ENABLE_PRECOMPILE) {
      if (Controller.options.sampling()) {
        // Create our set of standard optimization plans.
        Controller.recompilationStrategy.init();
      } else if (Controller.options.counters()) {
        InvocationCounts.init();
      }
      Controller.osrOrganizer = new OSROrganizerThread();
      Controller.osrOrganizer.start();
      createCompilationThread();
      // We're running an AOS bootimage with a non-adaptive primary strategy.
      // We already set up any requested profiling infrastructure, so nothing
      // left to do but exit.
      controllerInitDone();
      // to have a fair comparison, we need to create the data structures
      // of organizers
      createOrganizerThreads();
      AOSLogging.logger.reportThatAOSIsInReplayMode();
      while (true) {
        if (opts.EARLY_EXIT && opts.EARLY_EXIT_TIME < Controller.controllerClock) {
          Controller.stop();
        }
        Object event = Controller.controllerInputQueue.deleteMin();
        ((OnStackReplacementEvent) event).process();
      }

    }

    // Initialize the CompilerDNA class
    // This computes some internal options, must be done early in boot process
    CompilerDNA.init();

    // Create the organizerThreads and schedule them
    createOrganizerThreads();

    // Create the compilationThread and schedule it
    createCompilationThread();

    if (Controller.options.sampling()) {
      // Create our set of standard optimization plans.
      Controller.recompilationStrategy.init();
    } else if (Controller.options.counters()) {
      InvocationCounts.init();

    }

    controllerInitDone();

    // Enter main controller loop.
    // Pull an event to process off of
    // Controller.controllerInputQueue and handle it.
    // If no events are on the queue, then the deleteMin call will
    // block until an event is available.
    // Repeat forever.
    while (true) {
      if (opts.EARLY_EXIT && opts.EARLY_EXIT_TIME < Controller.controllerClock) {
        Controller.stop();
      }
      Object event = Controller.controllerInputQueue.deleteMin();
      ((ControllerInputEvent) event).process();
    }
  }

  /**
   * Now that the controller is initialized, schedule all the organizer threads
   * and signal the sentinel object.
   */
  private void controllerInitDone() {
    for (Enumeration<Organizer> e = Controller.organizers.elements(); e.hasMoreElements();) {
      Organizer o = e.nextElement();
      o.start();
    }
    try {
      sentinel.open();
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to start up controller subsystem");
    }
  }

  /**
   * Called when the controller thread is about to wait on
   * Controller.controllerInputQueue
   */
  public void aboutToWait() {
  }

  /**
   * Called when the controller thread is woken after waiting on
   * Controller.controllerInputQueue
   */
  public void doneWaiting() {
    ControllerMemory.incrementNumAwoken();
  }

  /**
   * If we're going to be gathering a dynamic call graph, then we don't
   * want to let the opt compiler compile anything above O0 until we have
   * some initial data in the call graph to work with.  The goal of this
   * restriction is to avoid making early bad decisions that we don't get
   * a chance to revisit because methods get to maxOptLevel too quickly.
   *
   * @return {@code true} if we need to restrict the optimization levels
   *  to ensure that we don't make bad optimization decisions, {@code false}
   *  if no restriction is necessary
   */
  public boolean earlyRestrictOptLevels() {
    return dcgOrg != null && !dcgOrg.someDataAvailable();
  }

  ///////////////////////
  // Initialization.
  //  Create AOS threads.
  //  Initialize AOS data structures that depend on command line arguments.
  ///////////////////////

  /**
   *  Creates and schedules the compilationThread.
   */
  private void createCompilationThread() {
    CompilationThread ct = new CompilationThread();
    Controller.compilationThread = ct;
    ct.start();
  }

  /**
   * Create a dynamic call graph organizer of one doesn't already exist
   */
  private void createDynamicCallGraphOrganizer() {
    if (dcgOrg == null) {
      dcgOrg = new DynamicCallGraphOrganizer(new EdgeListener());
      Controller.organizers.add(dcgOrg);
    }
  }

  /**
   * Create profiling entities that are independent of whether or not
   * adaptive recompilation is actually enabled.
   */
  private void createProfilers() {
    if (Controller.options.GATHER_PROFILE_DATA) {
      Controller.organizers.add(new AccumulatingMethodSampleOrganizer());

      createDynamicCallGraphOrganizer();
    }
  }

  /**
   *  Create the organizerThreads and schedule them
   */
  private void createOrganizerThreads() {
    AOSOptions opts = Controller.options;

    if (opts.sampling()) {
      // Primary backing store for method sample data
      Controller.methodSamples = new MethodCountData();

      // Install organizer to drive method recompilation
      Controller.organizers.add(new MethodSampleOrganizer(opts.DERIVED_FILTER_OPT_LEVEL));
      // Additional set up for feedback directed inlining
      if (opts.ADAPTIVE_INLINING) {
        Organizer decayOrganizer = new DecayOrganizer(new YieldCounterListener(opts.DECAY_FREQUENCY));
        Controller.organizers.add(decayOrganizer);
        createDynamicCallGraphOrganizer();
      }
    }

    if ((!opts.ENABLE_PRECOMPILE) && (!opts.ENABLE_BULK_COMPILE)) {
      Controller.osrOrganizer = new OSROrganizerThread();
      Controller.osrOrganizer.start();
    }
  }

  /**
   * Final report
   */
  public static void report() {
    AOSLogging.logger.printControllerStats();
  }

}
