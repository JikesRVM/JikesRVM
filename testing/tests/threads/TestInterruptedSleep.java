/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM 2002
 */
// $Id$

/**
 * Test that Thread.sleep() can be interrupted.
 *
 * @author David Hovemeyer
 */
public class TestInterruptedSleep {

  public static void main(String[] argv) {
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

    try {
      Thread.sleep(2000);
    }
    catch (InterruptedException e) {
      System.out.println("TestInterruptedSleep SUCCESS");
      System.exit(0);
    }

    System.out.println("TestInterruptedSleep FAILED");
  }

}
