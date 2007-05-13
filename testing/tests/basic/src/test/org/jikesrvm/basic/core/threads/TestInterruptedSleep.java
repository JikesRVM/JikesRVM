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
 * Test that Thread.sleep() can be interrupted.
 *
 */
public class TestInterruptedSleep {
  private static volatile boolean started;
  private static volatile boolean sleeping;

  public static void main(String[] argv) {
    final Thread main = Thread.currentThread();

    new Thread() {
      public void run() {
        started = true;
        while (!sleeping) Thread.yield();
        try {
          Thread.sleep(1000);
        }
        catch (InterruptedException e) {
        }
        main.interrupt();
      }
    }.start();

    while (!started) Thread.yield();
    try {
      sleeping = true;
      Thread.sleep(2000);
      System.out.println("TestInterruptedSleep FAILED");
    }
    catch (InterruptedException e) {
      System.out.println("TestInterruptedSleep SUCCESS");
    }
  }
}
