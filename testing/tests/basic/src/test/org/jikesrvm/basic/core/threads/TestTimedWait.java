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
 * Test Timed Wait.
 * <p/>
 * monitorenter
 * monitorexit
 * <p/>
 * wait
 * wait(millis)
 * notify
 * notifyAll
 */

class TestTimedWait {
  public static void main(String[] args) {
    Flag.timedWait = true;

    int count = 20;
    Mailbox mailbox = new Mailbox(count);
    Flag flag = new Flag();
    Task[] tasks = new Task[count];

    for (int i = 0; i < count; ++i) {
      tasks[i] = new Task(mailbox, flag, i);
    }

  for (Task task : tasks) {
      task.start();
      //Have to wait for the messages to be sent
      //otherwises replies will be received in wrong
      //order if method is part-way through a compile
      while(!task.sent) { Thread.yield(); }
    }

    // wait for mailbox to fill up
    mailbox.await();

    // display mail
    for (int i = 0; i < count; ++i) {
      XThread.say(mailbox.messages[i] + " replied");
    }

    // pause to allow tasks to queue up on flag
    XThread.say("sleeping");
    try { Thread.sleep(1000); } catch (InterruptedException e) {}
    XThread.say("running");

    // release tasks waiting on flag, letting them terminate
    flag.set();

    // wait for them to terminate
    XThread.say("bye");
    XThread.outputMessages();
  }
}
