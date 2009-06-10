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

class Mailbox {
  final String[] messages;
  int received;

  Mailbox(int max) {
    messages = new String[max];
  }

  // Add a message to this mailbox.
  //
  synchronized void send(String message) {
    messages[received++] = message;
    if (received == messages.length) {
      XThread.say("mailbox: notification sent to tell main that mailbox is full");
      notify();
    }
  }

  // Wait for this mailbox to fill up.
  //
  synchronized void await() {
    if (received != messages.length) {
      try { wait(); } catch (InterruptedException e) {}
    }
    XThread.say("mailbox: notification received");
  }
}
