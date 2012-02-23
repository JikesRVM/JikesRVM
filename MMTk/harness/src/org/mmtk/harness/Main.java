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
package org.mmtk.harness;


import org.mmtk.harness.lang.Checker;
import org.mmtk.harness.lang.CheckerException;
import org.mmtk.harness.lang.Compiler;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.parser.Parser;
import org.mmtk.harness.scheduler.Scheduler;

/**
 * Main class for the MMTk debugging harness.
 */
public class Main {

  /**
   * @param args Command line parameters
   * @throws Exception Something went wrong
   */
  public static void main(String[] args) throws Exception {
    /* Usage */
    if (args.length < 1) {
      System.err.println("usage: java -jar mmtk-harness.jar test-script [options ...]");
      System.exit(-1);
    }

    /* First argument is the test script name */
    String[] harnessArgs = new String[args.length - 1];
    System.arraycopy(args, 1, harnessArgs, 0, harnessArgs.length);

    /* Initialise the harness */
    Harness.init(harnessArgs);

    /* Parse the script */
    String scriptFile = args[0];
    if(!scriptFile.endsWith(".script")) {
      scriptFile += ".script";
    }

    /* Compile the script */
    final MethodTable methods = new Parser(scriptFile).script();

    try {
      /* Type-check the script */
      Checker.typeCheck(methods);
    } catch (CheckerException e) {
      System.err.println(e.getMessage());
      System.err.println("Exiting due to type-checker exceptions");
      exitWithFailure();
    }

    try {
      TimeoutThread timeout = new TimeoutThread(Harness.timeout.getValue());

      /* Schedule a thread to run the script */
      Scheduler.scheduleMutator(Compiler.compile(methods));

      /* Start the thread scheduler */
      Scheduler.schedule();

      timeout.cancel();

      Harness.mmtkShutdown();
    } catch (Throwable e) {
      e.printStackTrace();
      exitWithFailure();
    }

    exitWithSuccess();
  }

  /**
   * Exit, giving a visible success message
   */
  public static void exitWithSuccess() {
    System.out.println("SUCCESS");
    System.exit(0);
  }

  /**
   * Exit, giving a visible failure message
   * @param code Return status
   */
  public static void exitWithFailure(int code) {
    System.out.println("FAIL");
    System.exit(code);
  }

  /**
   * Exit, giving a visible failure message
   */
  public static void exitWithFailure() {
    exitWithFailure(1);
  }
}
