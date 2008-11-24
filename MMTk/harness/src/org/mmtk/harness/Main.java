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
package org.mmtk.harness;


import java.io.FileNotFoundException;

import org.mmtk.harness.lang.Checker;
import org.mmtk.harness.lang.Compiler;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.parser.Parser;
import org.mmtk.harness.lang.parser.ParseException;
import org.mmtk.harness.scheduler.Scheduler;

public class Main {
  public static void main(String[] args) throws InterruptedException, ParseException, FileNotFoundException {
    /* Usage */
    if (args.length < 1) {
      System.err.println("usage: java -jar mmtk-harness.jar test-script [options ...]");
      System.exit(1);
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

    final MethodTable methods = new Parser(scriptFile).script();

    /* Type-check the script */
    Checker.typeCheck(methods);


    Scheduler.scheduleMutator(Compiler.compile(methods));

    Scheduler.schedule();
  }
}
