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
 * Test Back Edge GC
 *
 *    The classes in this file test the back edge call to GC
 *
 * An object of this class loops creates a Call GC object
 *     then loops until a GC is done by a different object
 *        then it starts the GC object that it created
 */

/**
 * Create the Looper object and start
 * Create the GC object
 * wait until the looper object is started
 * start the GC object
 * wait for the GC object to complete, then exit
 */
class TestBackEdgeGC {
  public static void main(String[] args) throws java.lang.InterruptedException {

    XThread.say("Creating Looper");
    Looper looper = new Looper();

    XThread.say("Creating CallGC");
    CallGC callGC = new CallGC(1);

    XThread.say("Starting Looper");
    looper.start();

    // start Thread2 after thread 1 is in loop
    while (!looper.running) {
      try { Thread.sleep(20); } catch (InterruptedException e) {}
    }
    XThread.say("Looper running -starting CallGC");
    callGC.start();

    // wait for GC thread to complete
    XThread.say("waiting for join with callGC");
    callGC.join();

    XThread.say("bye");

    XThread.outputMessages();
  }

  /**
   * Objects of this class invokes GC and exit
   */
  static class CallGC extends XThread {

    int id = 0;

    CallGC(int cnt) {
      super("CallGC");
      //save id fiels
      this.id = cnt;
    }

    void performTask() {
      XThread.say("calling GC id = " + id);
      System.gc();
      XThread.say("GC complete- id = " + id);
      Looper.gccomplete = true;
      XThread.say("exiting id = " + id);
    }
  }

  static class Looper extends XThread {
    static boolean gccomplete;

    Looper() { super("Looper"); }

    void performTask() {
      CallGC gc = new CallGC(2);
      XThread.say("2nd CallGC created");

      // Loop until a separate gc is complete
      while (!gccomplete) {
      }

      // start a second gc- using previously created object and test if gc field is valid
      XThread.say("Starting 2nd CallGC");
      gc.start();
      try { gc.join(); } catch (InterruptedException e) {}
      XThread.say("2nd CallGC started - exiting Looper");
    }
  }
}







