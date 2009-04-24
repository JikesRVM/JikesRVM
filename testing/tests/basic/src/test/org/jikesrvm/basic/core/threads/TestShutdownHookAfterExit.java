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
package test.org.jikesrvm.basic.core.threads;

class TestShutdownHookAfterExit extends Thread {
  public void run() {
    System.out.println("Shutdown hook called.");
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Adding hook.");
    Runtime.getRuntime().addShutdownHook(new TestShutdownHookAfterExit());
    System.out.println("Exiting main.");
    System.exit(0);
  }
}
