/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/threads/src/TestBackEdgeGC.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
package test.org.jikesrvm.basic.core.threads;

/**
 * Test notification.
 * <p/>
 * monitorenter
 * monitorexit
 * <p/>
 * wait
 * notify
 * notifyAll
 *
 * @author unascribed
 */
class TestNotification {
  public static void main(String args[]) {
    int count = 20;
    Mailbox mailbox = new Mailbox(count);
    Flag flag = new Flag();
    final Task[] tasks = new Task[count];

    for (int i = 0; i < tasks.length; ++i) {
      tasks[i] = new Task(mailbox, flag, i);
    }

    for (Task task : tasks) {
      task.start();
      //Have to wait for the messages to be sent
      //otherwises replies will be received in wrong
      //order if method is part-way through a compile
      while( !task.sent ) { Thread.yield(); }
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

    XThread.say("bye");
    XThread.outputMessages();
  }
}
