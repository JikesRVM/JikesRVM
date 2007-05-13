/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.core.threads;

class TestDispatch {

  public static void main(String[] args) {

    final int threadCount = 2;
    final TestDispatchWorker[] workers = new TestDispatchWorker[threadCount];
    for (int i = 0; i < threadCount; i++) {
      workers[i] = new TestDispatchWorker("worker " + i);
    }
    for (final TestDispatchWorker worker : workers) {
      worker.start();
    }

    boolean done = false;
    while (!done) {
      Thread.yield();
      done = true;
      for (final TestDispatchWorker worker : workers) {
        done &= worker.completed;
      }
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static class TestDispatchWorker extends XThread {

    TestDispatchWorker(String name) {
      super(name);
    }

    void performTask() {
      for (int i = 0; i < 4; ++i) {
        XThread.say("sleeping");
        try { sleep(1000); } catch (InterruptedException e) {}
        XThread.say("running");
        System.gc();
        XThread.say("gc completed");
      }
    }
  }
}


