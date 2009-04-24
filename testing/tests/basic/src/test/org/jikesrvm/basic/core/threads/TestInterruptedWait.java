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
 * Test to ensure that monitor waits can be interrupted.
 */
public class TestInterruptedWait {
  private static volatile boolean started;
  private static volatile boolean locking;

  public static void main(String[] argv) {
    final Object lock = new Object();

    final Thread main = Thread.currentThread();

    new Thread() {
      public void run() {
        started = true;
        while (!locking) Thread.yield();
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        main.interrupt();
      }
    }.start();

    while (!started) Thread.yield();
    synchronized (lock) {
      locking = true;
      try {
        lock.wait(2000);
      } catch (InterruptedException e) {
        System.out.println("TestInterruptedWait SUCCESS");
        System.exit(0);
      }
    }

    System.out.println("TestInterruptedWait FAILED");
  }
}
