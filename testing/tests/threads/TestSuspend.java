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
 * @author unascribed
 */
class TestSuspend extends Thread {
  
  static Thread sleeper;

  public static void main(String args[]) throws Exception {
    System.out.println("TestSuspend");
    sleeper = Thread.currentThread();
    TestSuspend waker = new TestSuspend();
    waker.start();
    System.out.println(Thread.currentThread().getName() + ": suspending");
    sleeper.suspend();
    System.out.println(Thread.currentThread().getName() + ": resumed");
    System.out.println("main: bye");
  }
  
  public void run() {
    try {
      Thread.currentThread().sleep(5000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    sleeper.resume();
  }
  
}
