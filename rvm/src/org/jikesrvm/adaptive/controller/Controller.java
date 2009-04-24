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
import java.util.Vector;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.OSROrganizerThread;
import org.jikesrvm.adaptive.database.AOSDatabase;
import org.jikesrvm.adaptive.database.callgraph.PartialCallGraph;
import org.jikesrvm.adaptive.database.methodsamples.MethodCountData;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.instrumentation.Instrumentation;
import org.jikesrvm.adaptive.measurements.organizers.Organizer;
import org.jikesrvm.adaptive.recompilation.CompilationThread;
import org.jikesrvm.adaptive.recompilation.instrumentation.CounterBasedSampling;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.adaptive.util.AOSOptions;
import org.jikesrvm.adaptive.util.BlockingPriorityQueue;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.common.RecompilationManager;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.SoftLatch;

/**
 * This class contains top level adaptive compilation subsystem functions.
 */
public class Controller implements Callbacks.ExitMonitor,
                                   Callbacks.RecompileAllDynamicallyLoadedMethodsMonitor {

  /**
   * Signals when the options and (optional) logging mechanism are enabled
   */
  public static boolean enabled = false;

  /**
   * Controller subsystem control options
   */
  public static final AOSOptions options = new AOSOptions();

  /**
   * Deferred command line arguments for the opt compiler
   */
  private static String[] optCompilerOptions = new String[0];

  /**
   * Add a deferred command line argument
   */
  public static void addOptCompilerOption(String arg) {
    String[] tmp = new String[optCompilerOptions.length + 1];
    for (int i = 0; i < optCompilerOptions.length; i++) {
      tmp[i] = optCompilerOptions[i];
    }
    tmp[optCompilerOptions.length] = arg;
    optCompilerOptions = tmp;
  }

  /**
   * Get the deferred command line arguments
   */
  public static String[] getOptCompilerOptions() {return optCompilerOptions;}

  /**
   * The controller thread, it makes all the decisions
   * (the thread sets this field when it is created.)
   */
  public static ControllerThread controllerThread = null;

  /**
   * Thread that will perform opt-compilations as directed by the controller
   * (the thread sets this field when it is created.)
   */
  public static CompilationThread compilationThread = null;

  /**
   * Thread collecting osr request and pass it to controllerThread
   */
  public static OSROrganizerThread osrOrganizer = null;

  /**
   * Threads that will organize profile data as directed by the controller
   */
  public static final Vector<Organizer> organizers = new Vector<Organizer>();

  /**
   * A blocking priority queue where organizers place events to
   * be processed by the controller
   * (an input to the controller thread)
   */
  public static BlockingPriorityQueue controllerInputQueue;

  /**
   * A blocking priority queue where the controller will place methods
   * to be opt compiled
   * (an output of the controller thread)
   */
  public static BlockingPriorityQueue compilationQueue;

  /**
   * The strategy used to make recompilation decisions
   */
  public static RecompilationStrategy recompilationStrategy;

  /**
   * Controller virtual clock, ticked every taken yieldpoint.
   */
  public static int controllerClock = 0;

  /**
   * The main hot method raw data object.
   */
  public static MethodCountData methodSamples;
  /**
   * The dynamic call graph
   */
  public static PartialCallGraph dcg;

  /**
   * Used to shut down threads
   */
  private static final ThreadDeath threadDeath = new ThreadDeath();

  /**
   * Has the execution of boot completed successfully?
   */
  private static boolean booted = false;

  /**
   * Initialize the controller subsystem (called from VM.boot)
   * This method is called AFTER the command line options are processed.
   */
  public static void boot() {
    // Signal that the options and (optional) logging mechanism are set
    // RuntimeCompiler checks this flag
    enabled = true;

    // Initialize the controller input queue
    controllerInputQueue = new BlockingPriorityQueue(new BlockingPriorityQueue.CallBack() {
      public void aboutToWait() { controllerThread.aboutToWait(); }
      public void doneWaiting() { controllerThread.doneWaiting(); }
    });

    compilationQueue = new BlockingPriorityQueue();

    // Create the analytic model used to make cost/benefit decisions.
    recompilationStrategy = new MultiLevelAdaptiveModel();

    // boot the runtime measurement systems
    RuntimeMeasurements.boot();

    // Initialize subsystems, if being used
    AdaptiveInlining.boot(options);

    // boot any instrumentation options
    Instrumentation.boot(options);

    // boot the aos database
    AOSDatabase.boot(options);

    CounterBasedSampling.boot(options);

    createControllerThread();

    Controller controller = new Controller();
    Callbacks.addExitMonitor(controller);

    // make sure the user hasn't explicitly prohibited this functionality
    if (!options.DISABLE_RECOMPILE_ALL_METHODS) {
      Callbacks.addRecompileAllDynamicallyLoadedMethodsMonitor(controller);
    }

    booted = true;
  }

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    report();
  }

  /**
   * Called when the application wants to recompile all dynamically
   *  loaded methods.  This can be expensive!
   */
  public void notifyRecompileAll() {
    AOSLogging.logger.recompilingAllDynamicallyLoadedMethods();
    RecompilationManager.recompileAllDynamicallyLoadedMethods(false);
  }

  // Create the ControllerThread
  static void createControllerThread() {
    SoftLatch sentinel = new SoftLatch(false);
    ControllerThread tt = new ControllerThread(sentinel);
    tt.start();
    // wait until controller threads are up and running.
    try {
      sentinel.waitAndClose();
    } catch (Exception e) {
      e.printStackTrace();
      VM.sysFail("Failed to start up controller subsystem");
    }
  }

  /**
   * Process any command line arguments passed to the controller subsystem.
   * <p>
   * This method has the responsibility of creating the options object
   * if it does not already exist
   * <p>
   * NOTE: All command line argument processing should be handled via
   * the automatically generated code in AOSOptions.java.
   * Don't even think of adding handwritten stuff here! --dave
   *
   * @param arg the command line argument to be processed
   */
  public static void processCommandLineArg(String arg) {
    if (!options.processAsOption("-X:aos", arg)) {
      VM.sysWrite("vm: illegal adaptive configuration directive \"" + arg + "\" specified as -X:aos:" + arg + "\n");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }

  /**
   * This method is called when the VM is exiting to provide a hook to allow
   * the adpative optimization subsystem to generate a summary report.
   * It can also be called directly from driver programs to allow
   * reporting on a single run of a benchmark that the driver program
   * is executing in a loop (in which case the adaptive system isn't actually
   * exiting.....so some of the log messages may get a little wierd).
   */
  public static void report() {
    if (!booted) return;
    ControllerThread.report();
    RuntimeMeasurements.report();

    for (Enumeration<Organizer> e = organizers.elements(); e.hasMoreElements();) {
      Organizer organizer = e.nextElement();
      organizer.report();
    }

    if (options.FINAL_REPORT_LEVEL >= 2) {
      EdgeCounts.dumpCounts();
      dcg.dumpGraph();
    }

    if (options.REPORT_INTERRUPT_STATS) {
      VM.sysWriteln("Timer Interrupt and Listener Stats");
      VM.sysWriteln("\tTotal number of clock ticks ", RVMThread.timerTicks);
      VM.sysWriteln("\tController clock ", controllerClock);
      VM.sysWriteln("\tNumber of method samples taken ", (int) methodSamples.getTotalNumberOfSamples());
    }
  }

  /**
   * Stop all AOS threads and exit the adaptive system.
   * Can be used to assess code quality in a steady state by
   * allowing the adaptive system to run "for a while" and then
   * stopping it
   */
  public static void stop() {
    if (!booted) return;

    VM.sysWriteln("AOS: Killing all adaptive system threads");
    for (Enumeration<Organizer> e = organizers.elements(); e.hasMoreElements();) {
      Organizer organizer = e.nextElement();
      organizer.stop(threadDeath);
    }
    compilationThread.stop(threadDeath);
    controllerThread.stop(threadDeath);
    RuntimeMeasurements.stop();
    report();
  }
}

