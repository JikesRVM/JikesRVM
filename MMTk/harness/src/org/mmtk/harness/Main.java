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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mmtk.harness.lang.Checker;
import org.mmtk.harness.lang.CheckerException;
import org.mmtk.harness.lang.Compiler;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.WatchedVariables;
import org.mmtk.harness.lang.ast.OptionDef;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.parser.GlobalDefs;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.parser.Parser;
import org.mmtk.harness.lang.runtime.StackAllocator;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.harness.vm.Memory;
import org.vmmagic.unboxed.Extent;

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

    /* Parse the script */
    String scriptFile = args[0];
    if(!scriptFile.endsWith(".script")) {
      scriptFile += ".script";
    }
    /* First argument is the test script name */
    List<String> harnessArgs = new ArrayList<String>(Arrays.asList(args));
    harnessArgs.remove(0);

    Harness.initArchitecture(harnessArgs);

    /* Compile the script */
    final GlobalDefs script = new Parser(scriptFile).script();
    final MethodTable methods = script.getMethods();

    try {
      /* Type-check the script */
      Checker.typeCheck(methods);
    } catch (CheckerException e) {
      System.err.println(e.getMessage());
      System.err.println("Exiting due to type-checker exceptions");
      exitWithFailure();
    }

    /*
     * Add any options defined in the script
     *
     * They are overridden by options on the command line
     */
    for (OptionDef option : script.getOptions()) {
      harnessArgs.add(0, option.toCommandLineArg());
    }

    /* Initialise the harness */
    Harness.init(harnessArgs);

    Env.setStackSpace(new StackAllocator(
        Memory.getHeapstartaddress(),
        Memory.getVmspacesize(),
        Extent.fromIntZeroExtend(1024*1024)));
    CompiledMethod.setWatchedVariables(new WatchedVariables());

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
