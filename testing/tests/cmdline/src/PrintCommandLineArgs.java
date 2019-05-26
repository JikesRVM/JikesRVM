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

import org.jikesrvm.runtime.CommandLineArgs;

public class PrintCommandLineArgs {
  public static void main(String[] args) {
    System.out.println("Application command line args in order: ");
    for (int i = 0; i < args.length; i++) {
      System.out.println(args[i]);
    }
    System.out.println("VM command line args in order: ");
    String[] inputArgs = CommandLineArgs.getInputArgs();
    for (int i = 0; i < inputArgs.length; i++) {
      System.out.println(inputArgs[i]);
    }
    System.out.println("ALL TESTS PASSED");
  }
}
