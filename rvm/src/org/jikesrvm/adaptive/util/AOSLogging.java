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
package org.jikesrvm.adaptive.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerMemory;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.controller.HotMethodEvent;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.runtime.Time;

/**
 * This class provides logging functionality for the Adaptive Optimization System
 *
 * Right now this is fairly primitive, an evolving number of events are
 * defined and log entries are quite unsophisticated.
 * Some obvious TODO items:
 *  -- compact encoding of log entries
 *  -- some notion of log format versions
 *  -- ...
 *
 * NOTE: All code that writes to the log is synchronized on the PrintStream
 *      object to avoid interspersed messages, which can happen when the
 *      compilation thread and the controller thread try to log a message
 *      "at the same time".
 *
 * ***When is the log file flushed and closed?
 * ***Do we want to put report() information in the log?
 *
 * The current logging levels are:
 *   0  Do no logging
 *   1  Do minimal logging at startup and VM exit.
 *      If at all possible, do not log anything during program execution.
 *      This logging level is supposed to produce minimal performance pertubation.
 *   2  Log interesting AOS events and controller actions
 *   3  Exhaustively log pretty much everything that is going on
 */
public final class AOSLogging {

  /** Singleton instance of the logger */
  public static final AOSLogging logger = new AOSLogging();

  /*
   * The output file stream, where all log messages will go
   */
  private PrintStream log;

  /**
   * Returns the log object
   */
  public PrintStream getLog() {
    return log;
  }

  /*
  * Record that the AOS logging has been booted.
  * Needed to allow fast exit from reporting to ensure
  * that when no class is specified to be run but "-help" is specified,
  * don't want null pointer exception to occur!
  */
  private boolean booted = false;

  /**
   * Return whether AOS logging has booted.
   * @return whether AOS logging has booted
   */
  public boolean booted() {
    return booted;
  }

  /**
   * Helper routine to produce the current time as a string
   */
  private String getTime() {
    return Controller.controllerClock + ":" + Time.nanoTime();
  }

  /**
   * Called from ControllerThread.run to initialize the logging subsystem
   */
  public void boot() {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      try {
        log = new PrintStream(new FileOutputStream(Controller.options.LOGFILE_NAME));

        // This statement will force the compilation of println, so it
        // is needed regardless of the particular content of the message!
        synchronized (log) {
          log.println(getTime() + " Logging enabled\n");
          log.println(Controller.options);
        }
      } catch (IOException e) {
        VM.sysWrite("IOException caught in AOSLogging.java while trying to create and start log file.\n");
        VM.sysWrite("Please check for file permission problems\n");
      }
    }
    booted = true;
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 1
  ////////////////////////////////////////////////////////////////

  /**
   * Call this method to dump statistics related to decaying
   * @param decayCount the number of decay events
   */
  public void decayStatistics(int decayCount) {
    if (!booted) return; // fast exit
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.print(getTime() + " Decay Organizer Statistics: \n\t" + " Num of Decay events: " + decayCount + "\n");
      }
    }
  }

  /**
   * Call this method when one run of the application begins
   */
  public void recompilingAllDynamicallyLoadedMethods() {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " Recompiling all dynamically loaded methods");
      }
    }
  }

  /**
   * Dumps lots of controller stats to the log file
   */
  public void printControllerStats() {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      int awoken = ControllerMemory.getNumAwoken();
      int didNothing = ControllerMemory.getNumDidNothing();
      int numMethodsConsidered = ControllerMemory.getNumMethodsConsidered();
      int numMethodsScheduledForRecomp = ControllerMemory.getNumMethodsScheduledForRecomp();
      int numOpt0 = ControllerMemory.getNumOpt0();
      int numOpt1 = ControllerMemory.getNumOpt1();
      int numOpt2 = ControllerMemory.getNumOpt2();
      int numOpt3 = ControllerMemory.getNumOpt3();

      synchronized (log) {
        log.print(getTime() +
                  "\n  Num times Controller thread is awoken: " +
                  awoken +
                  "\n  Num times did nothing: " +
                  didNothing +
                  " (" +
                  ((int) ((float) didNothing / (float) awoken * 100)) +
                  "%)\n  Num methods baseline compiled: " +
                  ControllerMemory.getNumBase() +
                  "\n  Num methods considered for recompilation: " +
                  numMethodsConsidered +
                  "\n  Num methods chosen to recompile: " +
                  numMethodsScheduledForRecomp +
                  " (" +
                  ((int) ((float) numMethodsScheduledForRecomp / numMethodsConsidered * 100)) +
                  "%)\n  Opt Levels Chosen: " +
                  "\n\t Opt Level 0: " +
                  numOpt0 +
                  " (" +
                  ((int) ((float) numOpt0 / numMethodsScheduledForRecomp * 100)) +
                  "%)\n\t Opt Level 1: " +
                  numOpt1 +
                  " (" +
                  ((int) ((float) numOpt1 / numMethodsScheduledForRecomp * 100)) +
                  "%)\n" +
                  "\t Opt Level 2: " +
                  numOpt2 +
                  " (" +
                  ((int) ((float) numOpt2 / numMethodsScheduledForRecomp * 100)) +
                  "%)\n" +
                  "\t Opt Level 3: " +
                  numOpt3 +
                  " (" +
                  ((int) ((float) numOpt3 / numMethodsScheduledForRecomp * 100)) +
                  "%)\n\n");

        // Let the controller memory summarize itself to the log file
        ControllerMemory.printFinalMethodStats(log);
      }
    }
  }

  /**
   * This method reports the basic speedup rate for a compiler
   * @param compiler the compiler you are reporting about
   * @param rate the speedup rate
   */
  public void reportSpeedupRate(int compiler, double rate) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() +
                    " SpeedupRate for " +
                    CompilerDNA.getCompilerString(compiler) +
                    " compiler: " +
                    rate);
      }
    }
  }

  /**
   * This method reports the basic compilation rate for a compiler
   * @param compiler the compiler you are reporting about
   * @param rate the compilation rate (bytecodes per millisecond)
   */
  public void reportCompilationRate(int compiler, double rate) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() +
                    " Compilation Rate (bytecode/msec) for " +
                    CompilerDNA.getCompilerString(compiler) +
                    " compiler: " +
                    rate);
      }
    }
  }

  /**
   *  This method reports the benefit ratio from one compiler to the other
   *  @param compiler1 the first compiler
   *  @param compiler2 the second compiler
   *  @param rate the improvement from going from a compiler1-compiled method
   *                   to a compiler2-compiled method
   */
  public void reportBenefitRatio(int compiler1, int compiler2, double rate) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() +
                    " Benefit Ratio from " +
                    CompilerDNA.getCompilerString(compiler1) +
                    " compiler to " +
                    CompilerDNA.getCompilerString(compiler2) +
                    " compiler: " +
                    rate);
      }
    }
  }

  /**
   *  This method reports the compile time ratio from one compiler to
   *  the other
   *  @param compiler1 the first compiler
   *  @param compiler2 the second compiler
   *  @param rate the ratio of compiler1 compilation rate to
   *                compiler2 compilation rate
   */
  public void reportCompileTimeRatio(int compiler1, int compiler2, double rate) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() +
                    " Compile Time Ratio of " +
                    CompilerDNA.getCompilerString(compiler1) +
                    " compiler to " +
                    CompilerDNA.getCompilerString(compiler2) +
                    " compiler: " +
                    rate);
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 2
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs the scheduling of a recompilation,
   * i.e., it being inserted in the compilation queue.
   * @param plan the Compilation plan being executed.
   * @param priority a number from 0.0 to 1.0 encoding the plan's priority.
   */
  public void recompilationScheduled(CompilationPlan plan, double priority) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " Scheduling level " + plan.options.getOptLevel() + " recompilation of " + plan
            .method + " (plan has priority " + priority + ")");
      }
    }
  }

  /**
   * This method logs the beginning of an adaptively selected recompilation
   * @param plan the Compilation plan being executed.
   */
  public void recompilationStarted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " Recompiling (at level " + plan.options.getOptLevel() + ") " + plan.method);
      }
    }
  }

  /**
   * This method logs the successful completion of an adaptively
   * selected recompilation
   * @param plan the Compilation plan being executed.
   */
  public void recompilationCompleted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        //        log.println(getTime() +"  Recompiled (at level "+
        //                    plan.options.getOptLevel() +") " +plan.method);
        log.println(getTime() + "  Recompiled (at level " + plan.options.getOptLevel() + ") " + plan.method);
      }
    }
  }

  /**
   * This method logs the abortion of an adaptively selected recompilation
   * @param plan the Compilation plan being executed.
   */
  public void recompilationAborted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " Failed recompiling (at level " + plan.options.getOptLevel() + " " + plan.method);
      }
    }
  }

  /**
   * This method logs the actual compilation time for the given compiled method.
   * @param cm the compiled method
   * @param expectedCompilationTime the model-derived expected compilation time
   */
  public void recordCompileTime(CompiledMethod cm, double expectedCompilationTime) {
    if (log != null && Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        double compTime = cm.getCompilationTime();
        log.println(getTime() +
                    " Compiled " +
                    cm.getMethod() +
                    " with " +
                    cm.getCompilerName() +
                    " in " +
                    compTime +
                    " ms" +
                    ", model estimated: " +
                    expectedCompilationTime +
                    " ms" +
                    ", rate: " +
                    (((NormalMethod) cm.getMethod()).getBytecodeLength() / compTime));
      }
    }
  }

  /**
   * this method logs the event when the controller discovers a method that has
   * been recompiled and the previous version is still regarded as hot,
   * i.e., still on the stack and signficant.
   */
  public void oldVersionStillHot(HotMethodEvent hme) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " Found a method with an old version still hot " + hme);
      }
    }
  }

  /**
   * This method logs when the decay organizer runs.
   */
  public void decayingCounters() {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " Decaying clock and decayable objects");
      }
    }
  }

  /**
   * This Method logs when the organizer thread has reached its
   * sampling threshold
   */
  public void organizerThresholdReached() {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + " OrganizerThread reached sample size threshold\n");
      }
    }
  }

  /**
   * This method logs that the controller is notified of a
   * candidate to be recompiled due to hotness;
   * i.e., the method has been inserted in the controller queue.
   * @param hotMethod   method to be recompiled, and
   * @param numSamples  number of samples attributed to the method
   */
  public void controllerNotifiedForHotness(CompiledMethod hotMethod, double numSamples) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() +
                    " Controller notified that method " +
                    hotMethod.getMethod() +
                    "(" +
                    hotMethod.getId() +
                    ")" +
                    " has " +
                    numSamples +
                    " samples");
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 3
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs a controller cost estimate for doing nothing.
   * @param method the method of interest
   * @param optLevel the opt level being estimated, -1 = baseline
   * @param cost  the computed cost for this method and level
   */
  public void recordControllerEstimateCostDoNothing(RVMMethod method, int optLevel, double cost) {
    if (Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        log.print(getTime() + "  Estimated cost of doing nothing (leaving at ");
        if (optLevel == -1) {
          log.print("baseline");
        } else {
          log.print("O" + optLevel);
        }
        log.println(") to " + method + " is " + cost);
      }
    }
  }

  /**
   * This method logs a controller cost estimate.
   * @param method the method of interest
   * @param choiceDesc a String describing the choice point
   * @param compilationTime the computed compilation cost for this method and level
   * @param futureTime the computed future time, including cost and execution
   */
  public void recordControllerEstimateCostOpt(RVMMethod method, String choiceDesc, double compilationTime,
                                                     double futureTime) {
    if (Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        log.println(getTime() +
                    "  Estimated cost of OPT compiling " +
                    method +
                    " at " +
                    choiceDesc +
                    " is " +
                    compilationTime +
                    ", total future time is " +
                    futureTime);
      }
    }
  }

  /**
   * Records lots of details about the online computation of a compilation rate
   * @param compiler compiler of interest
   * @param method the method
   * @param BCLength the number of bytecodes
   * @param totalBCLength cumulative number of bytecodes
   * @param MCLength size of machine code
   * @param totalMCLength cumulative size of machine code
   * @param compTime compilation time for this method
   * @param totalCompTime cumulative compilation time for this method
   * @param totalLogOfRates running sum of the natural logs of the rates
   * @param totalLogValueMethods number of methods used in the log of rates
   * @param totalMethods total number of methods
   */
  public void recordUpdatedCompilationRates(byte compiler, RVMMethod method, int BCLength, int totalBCLength,
                                                   int MCLength, int totalMCLength, double compTime,
                                                   double totalCompTime, double totalLogOfRates,
                                                   int totalLogValueMethods, int totalMethods) {

    if (Controller.options.LOGGING_LEVEL >= 3) {
      synchronized (log) {
        boolean backBranch = false;
        if (method instanceof NormalMethod) {
          backBranch = ((NormalMethod) method).hasBackwardsBranch();
        }
        log.println(getTime() +
                    "  Updated compilation rates for " +
                    RuntimeCompiler.getCompilerName(compiler) +
                    "compiler");
        log.println("\tmethod compiled: " + method);
        log.println("\tbyte code length: " + BCLength + ", Total: " + totalBCLength);
        log.println("\tmachine code length: " + MCLength + ", Total: " + totalMCLength);
        log.println("\tbackwards branch: " + (backBranch ? "yes" : "no"));
        log.println("\tcompilation time: " + compTime + ", Total: " + totalCompTime);
        log.println("\tRate for this method: " + BCLength / compTime + ", Total of Logs: " + totalLogOfRates);
        log.println("\tTotal Methods: " + totalMethods);
        log.println("\tNew Rate: " + Math.exp(totalLogOfRates / totalLogValueMethods));
      }
    }
  }

  public void compileAllMethodsCompleted() {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(Controller.controllerClock + "  Compiled all methods finished. ");
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // OSR-related code
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs the successful completion of an adaptively
   * selected recompilation
   * @param plan the Compilation plan being executed.
   */
  public void recordOSRRecompilationDecision(ControllerPlan plan) {
    CompilationPlan cplan = plan.getCompPlan();
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " recompile with OSR " + "( at level " + cplan.options.getOptLevel() + " ) " + cplan
            .method);
      }
    }
  }

  public void onStackReplacementStarted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " OSR starts " + "( at level " + plan.options.getOptLevel() + " ) " + plan.method);
      }
    }
  }

  public void onStackReplacementCompleted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " OSR ends " + "( at level " + plan.options.getOptLevel() + " ) " + plan.method);
      }
    }
  }

  public void onStackReplacementAborted(CompilationPlan plan) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " OSR failed " + "( at level " + plan.options.getOptLevel() + " ) " + plan.method);
      }
    }
  }

  public void logOsrEvent(String s) {
    if (Controller.options.LOGGING_LEVEL >= 1) {
      synchronized (log) {
        log.println(getTime() + " " + s);
      }
    }
  }

  public void debug(String s) {
    if (Controller.options.LOGGING_LEVEL >= 2) {
      synchronized (log) {
        log.println(getTime() + s);
      }
    }
  }
}
