/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
        }
        catch (InterruptedException e) {
        }

        main.interrupt();
      }
    }.start();

    while (!started) Thread.yield();
    synchronized (lock) {
      locking = true;
      try {
        lock.wait(2000);
      }
      catch (InterruptedException e) {
        System.out.println("TestInterruptedWait SUCCESS");
        System.exit(0);
      }
    }

    System.out.println("TestInterruptedWait FAILED");
  }
}
