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
