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

/**
 * Test that Thread.sleep() can be interrupted.
 */
public class TestInterruptedSleep2 {
  public static void main(String[] argv) {
    final Thread sleeper = new Thread() {
      @Override
      public void run() {
        boolean interrupted = false;
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          interrupted = true;
        }
        if (interrupted) {
          System.out.println("TestInterruptedSleep2 SUCCESS");
        } else {
          System.out.println("TestInterruptedSleep2 FAILED");
        }
      }
    };
    sleeper.start();
    sleeper.interrupt();
  }
}
