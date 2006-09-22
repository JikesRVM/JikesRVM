/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Test to ensure that monitor waits can be interrupted.
 * @author David Hovemeyer
 */
public class TestInterruptedWait {

  public static void main(String[] argv) {
    final Object lock = new Object();

    final Thread mainThread = Thread.currentThread();

    new Thread() {
      public void run() {
        try {
          Thread.sleep(1000);
        }
        catch (InterruptedException e) {
        }

        mainThread.interrupt();
      }
    }.start();

    synchronized (lock) {
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
