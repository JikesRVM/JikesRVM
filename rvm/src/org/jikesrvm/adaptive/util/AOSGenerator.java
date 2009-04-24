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
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;

/**
 * This class provides advice file used by compile replay experiments
 * Right now this class is basically duplicate part of the AOSLogging
 * class.
 */
public class AOSGenerator {

  /*
   * The output file stream, where all log messages will go
   */
  private static PrintStream log;

  /*
  * Record that the AOS logging has been booted.
  * Needed to allow fast exit from reporting to ensure
  * that when no class is specified to be run but "-help" is specified,
  * don't want null pointer exception to occur!
  */
  private static boolean booted = false;

  // variable used to avoid recursive calls
  private static boolean recording = false;

  /**
   * Return whether AOS logging has booted.
   * @return whether AOS logging has booted
   */
  public static boolean booted() {
    return booted;
  }

  /**
   * Called from ControllerThread.run to initialize the logging subsystem
   */
  public static void boot() {
    VM.sysWrite("AOS generation booted\n");
    try {
      log = new PrintStream(new FileOutputStream(Controller.options.COMPILATION_ADVICE_FILE_OUTPUT));
    } catch (IOException e) {
      VM.sysWrite("IOException caught in AOSGenerator.java while trying to create and start log file.\n");
      VM.sysWrite("Please check for file permission problems\n");
    }
    booted = true;
    recording = false;
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 2
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs the successful completion of an adaptively
   * selected recompilation
   * @param plan the Compilation plan being executed.
   */
  public static void reCompilationWithOpt(CompilationPlan plan) {
    if (!booted) return;
    synchronized (log) {
      log.println(plan.method.getDeclaringClass().getDescriptor() +
                  " " +
                  plan.method.getName() +
                  " " +
                  plan.method.getDescriptor() +
                  " 3 " +
                  /*it's always compiler*/
                  plan.options.getOptLevel());
    }
  }

  public static void baseCompilationCompleted(CompiledMethod cm) {
    if (recording || (!booted)) return;
    synchronized (log) {
      recording = true;
      log.println(cm.getMethod().getDeclaringClass().getDescriptor() +
                  " " +
                  cm.getMethod().getName() +
                  " " +
                  cm.getMethod().getDescriptor() +
                  " " +
                  cm.getCompilerType() +
                  " " +
                  /*it's always baseline compiler*/
                  "-1");
      recording = false;
    }
  }
}
