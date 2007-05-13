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
 */
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
