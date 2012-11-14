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

@SuppressWarnings("deprecation")
class TestSuspend extends XThread {

  static Thread sleeper;

  public static void main(String[] args) {
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
