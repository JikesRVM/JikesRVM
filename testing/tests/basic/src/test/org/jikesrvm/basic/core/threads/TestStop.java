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
class TestStop {
  public static void main(String[] args) throws Exception {
    final Worker w = new Worker();
    w.start();
    while (!w.running) {
      try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }

    XThread.say("sending interrupt");
    w.stop(new ClassNotFoundException());

    XThread.say("waiting for TestStopWorker to die");
    while (w.isAlive()) {
      try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }

    XThread.say("bye");
    XThread.outputMessages();
  }

  static class Worker extends XThread {
    Worker() { super("Worker"); }

    void performTask() {
      try {
        while (true) { Thread.yield(); }
      }
      catch (final Exception e) {
        tsay("received interrupt " + e);
      }
    }
  }
}
