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
 * Test to ensure that InterruptedException can't
 * be thrown out of thin air (i.e., backedge yieldpoints).
 * The original VM_Thread.externalInterrupt related code
 * was far too willing to throw InterruptedExceptions
 * in places where they should not have originated.
 *
 * @author David Hovemeyer
 */
public class TestInterruptAndSpin {
  public static void main(String[] argv) {
    try {

      // Ensure that there is another thread to run
      Thread t = new Thread() {
        public void run() {
          while (true)
            Thread.yield();
        }
      };
      t.setDaemon(true);
      t.start();

      Thread.currentThread().interrupt();

      // Long running loop, should yield at some point
      int count = 0;
      for (int i = 0; i < 10000000; ++i)
        count += i;
      System.out.println("count is " + count);

      if (count == 17)
        throw new InterruptedException("Dummy"); // placate compiler

      System.out.println("TestInterruptAndSpin SUCCESS");

    }
    catch (InterruptedException e){
      // This should not have happened
      System.out.println("TestInterruptAndSpin FAILED");
    }
  }
}
