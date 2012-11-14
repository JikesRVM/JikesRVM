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
class TestStop {
  public static void main(String[] args) {
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
      } catch (final Exception e) {
        tsay("received interrupt " + e);
      }
    }
  }
}
