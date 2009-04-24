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

public class TestParallelHardwareTrap {
  public static Object o = null;
  public static Object oLock = new Object();
  public static int done = 0;

  public static void main(String[] args) {
    System.out.println("Running: TestParallelHardwareTrap");
    for(int i=0; i<10; i++) {
      new Thread(new Runnable() {
        public void run() {
          for(int i=0; i < 10000; i++) {
            causeAndHandleNPE();
          }
          synchronized (oLock) {
            done++;
            oLock.notify();
          }
        }
      }).start();
    }
    synchronized (oLock) {
      while (done < 10) {
        try {
          oLock.wait();
        } catch (InterruptedException ie) {}
      }
    }
    System.out.println("Finished: TestParallelHardwareTrap");
  }

  public static boolean causeAndHandleNPE() {
    try {
      o.toString();
    } catch (NullPointerException npe) {
      return false;
    }
    return true;
  }
}
