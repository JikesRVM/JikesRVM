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
 * @author unascribed
 */
class TestSuspend extends XThread {

  static Thread sleeper;

  public static void main(String[] args) {
    XThread.holdMessages = false;
    sleeper = Thread.currentThread();
    new TestSuspend().start();
    XThread.say("suspending self");
    sleeper.suspend();
    XThread.say("resumed");
    XThread.say("bye");
    XThread.outputMessages();
  }

  public TestSuspend() {
    super("Resumer");
  }

  void performTask() {
    try {
      Thread.sleep(5000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    XThread.say("resume sleeper...");
    try {
      sleeper.resume();
    } catch (Exception e) {
      XThread.say("error during resume: " + e);
      System.exit(1);
    }
  }
}
