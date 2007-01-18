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
class Task extends XThread {
  final Mailbox mailbox;
  final Flag flag;
  final int taskId;
  volatile boolean sent;

  Task(Mailbox mailbox, Flag flag, int id) {
    super("Task" + id);
    this.mailbox = mailbox;
    this.flag = flag;
    this.taskId = id;
  }

  void performTask() {
    mailbox.send(String.valueOf(taskId));
    sent = true;
    try { sleep(200); } catch (InterruptedException e) {}

    XThread.say("waiting");
    flag.await();
    XThread.say("ending");
  }
}
