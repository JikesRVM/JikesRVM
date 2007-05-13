/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.core.threads;

class Flag {
  static boolean timedWait;
  boolean flag;

  // Set this flag.
  //
  synchronized void set() {
    XThread.say("flag: notification sent");
    flag = true;
    notifyAll();
  }

  // Wait for this flag to be set.
  //
  synchronized void await() {
    if (!flag) {

      if (timedWait) {
        while (!flag) {
          try { wait(1000000); } catch (InterruptedException e) {}
          if (!flag) { XThread.say("flag: timed out"); }
        }
      } else {
        try { wait(); } catch (InterruptedException e) {}
      }
      XThread.say("flag: notification received");
    } else {
      XThread.say("flag: already set");
    }
  }
}
