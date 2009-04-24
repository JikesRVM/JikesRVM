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
public class myMain {

  public static int compute(String[] args) {
    // System.out.println(args[0].substring(0,8) + "-----" + args[0].substring(12));
    if (args[0].startsWith("C thread") &&
        args[0].endsWith("Hello from C"))
      return 123;
    else
      return 456;
  }

  public static void main(String[] args) {
    // System.out.println(System.getProperty("java.class.path"));
    // System.out.println("   from Java: Hello World! ");
    // System.out.println("   I am thread: " + Thread.currentThread().getName());
    // VM.debugBreakpoint();
    // for (int i=0; i<args.length; i++) {
    //  System.out.println("   arg " + i + " : " + args[i]);
    // }

  }
}
