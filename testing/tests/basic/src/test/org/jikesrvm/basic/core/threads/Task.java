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
